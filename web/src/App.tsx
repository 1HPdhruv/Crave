import { useEffect, useMemo, useState, type ReactNode } from 'react'
import type { Session } from '@supabase/supabase-js'
import {
  categories as seedCategories,
  type Category,
  type CraveOrder,
  demoOrder,
  foods as seedFoods,
  getCustomizationPrice,
  initialFavorites,
  outlets as seedOutlets,
  pastOrders,
  type CartItem,
  type CustomizationOption,
  type Food,
  type OrderStatus,
  type Outlet,
  type Tone,
} from './data/campusData'
import {
  fetchFavoriteIds,
  fetchLiveCatalog,
  fetchPickupSlots,
  fetchUserOrders,
  placeLiveOrder,
  subscribeToOrder,
  setFavorite,
  type LiveCatalog,
  type LivePickupSlot,
} from './lib/backend'
import { isSupabaseConfigured, supabase } from './lib/supabase'

type Role = 'student' | 'vendor' | 'admin'
type ToastTone = 'orange' | 'ink' | 'green'
type FulfillmentMode = 'pickup' | 'delivery'

const money = (value: number) => `₹${value}`
const cartLineTotal = (item: CartItem) => (item.price + (item.customizationTotal ?? 0)) * item.quantity
const fulfillmentFee = (mode: FulfillmentMode) => mode === 'delivery' ? 45 : 26

function useRoute() {
  const [path, setPath] = useState(window.location.pathname || '/')

  useEffect(() => {
    const onPopState = () => setPath(window.location.pathname || '/')
    window.addEventListener('popstate', onPopState)
    return () => window.removeEventListener('popstate', onPopState)
  }, [])

  const navigate = (nextPath: string) => {
    window.history.pushState({}, '', nextPath)
    setPath(nextPath)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  return { path, navigate }
}

function App() {
  const { path, navigate } = useRoute()
  const [role, setRole] = useState<Role>('student')
  const [cart, setCart] = useState<CartItem[]>([])
  const [favorites, setFavorites] = useState(initialFavorites)
  const [catalog, setCatalog] = useState<LiveCatalog>({ outlets: seedOutlets, foods: seedFoods, categories: seedCategories })
  const [backendStatus, setBackendStatus] = useState<'loading' | 'live' | 'fallback'>('loading')
  const [backendError, setBackendError] = useState('')
  const [session, setSession] = useState<Session | null>(null)
  const [remoteOrders, setRemoteOrders] = useState<CraveOrder[]>([])
  const [pickupSlots, setPickupSlots] = useState<LivePickupSlot[]>([])
  const [liveSlotId, setLiveSlotId] = useState('')
  const [activeCategory, setActiveCategory] = useState('all')
  const [search, setSearch] = useState('')
  const [toast, setToast] = useState<{ message: string; tone: ToastTone } | null>(null)
  const [order, setOrder] = useState<CraveOrder>({ ...demoOrder })
  const [checkoutSlot, setCheckoutSlot] = useState('12:40 PM')
  const [fulfillmentMode, setFulfillmentMode] = useState<FulfillmentMode>('pickup')
  const [selectedCustomizations, setSelectedCustomizations] = useState<string[]>([])
  const [selectedOptions, setSelectedOptions] = useState<CustomizationOption[]>([])
  const [selectedQuantity, setSelectedQuantity] = useState(1)

  const segments = path.split('/').filter(Boolean)
  const page = segments[0] || 'home'
  const activeRole: Role = page.startsWith('vendor') ? 'vendor' : page.startsWith('admin') ? 'admin' : role
  const foodFromPath = page === 'food' ? catalog.foods.find((food) => food.id === segments[1]) : undefined

  useEffect(() => {
    if (!supabase || !isSupabaseConfigured) {
      setBackendStatus('fallback')
      return
    }

    let active = true
    const loadCatalog = async () => {
      try {
        const liveCatalog = await fetchLiveCatalog(supabase!)
        if (!active) return
        setCatalog(liveCatalog)
        setBackendStatus('live')
        setBackendError('')
      } catch (error) {
        if (!active) return
        setBackendStatus('fallback')
        setBackendError(error instanceof Error ? error.message : 'Live catalog unavailable')
      }
    }
    void loadCatalog()

    void supabase.auth.getSession().then(({ data }) => {
      if (active) setSession(data.session)
    })
    const { data: authListener } = supabase.auth.onAuthStateChange((_event, nextSession) => {
      if (active) setSession(nextSession)
    })
    return () => {
      active = false
      authListener.subscription.unsubscribe()
    }
  }, [])

  useEffect(() => {
    if (!session || !supabase || backendStatus !== 'live') return
    let active = true
    void Promise.all([fetchFavoriteIds(session.user.id, supabase), fetchUserOrders(session.user.id, supabase)]).then(([favoriteIds, orders]) => {
      if (!active) return
      setFavorites(favoriteIds)
      setRemoteOrders(orders)
      if (orders[0]) setOrder(orders[0])
    }).catch((error) => {
      if (active) setBackendError(error instanceof Error ? error.message : 'Could not load your Crave data')
    })
    return () => { active = false }
  }, [backendStatus, session])

  useEffect(() => {
    const outletId = cart[0]?.outletId
    if (!outletId || backendStatus !== 'live' || !supabase) {
      setPickupSlots([])
      setLiveSlotId('')
      return
    }
    let active = true
    const today = new Date().toISOString().slice(0, 10)
    void fetchPickupSlots(outletId, today, supabase).then((slots) => {
      if (!active) return
      setPickupSlots(slots)
      setLiveSlotId((current) => slots.some((slot) => slot.id === current) ? current : slots[0]?.id || '')
      if (slots[0]) setCheckoutSlot(slots[0].label)
    }).catch(() => {
      if (active) setPickupSlots([])
    })
    return () => { active = false }
  }, [backendStatus, cart])

  useEffect(() => {
    if (!session || !supabase || !order.backendId || backendStatus !== 'live') return
    return subscribeToOrder(order.backendId, (nextOrder) => {
      setOrder(nextOrder)
      setRemoteOrders((current) => current.map((item) => item.backendId === nextOrder.backendId ? nextOrder : item))
    }, supabase)
  }, [backendStatus, order.backendId, session])

  const showToast = (message: string, tone: ToastTone = 'orange') => {
    setToast({ message, tone })
    window.setTimeout(() => setToast(null), 2600)
  }

  const cartCount = cart.reduce((sum, item) => sum + item.quantity, 0)
  const cartTotal = cart.reduce((sum, item) => sum + cartLineTotal(item), 0)

  const addToCart = (food: Food, quantity = 1, note?: string, customizationTotal = 0, customizations: string[] = [], selectedOptions: CustomizationOption[] = []) => {
    if (cart.length && cart[0].outletId !== food.outletId) {
      showToast('One outlet per order — finish this bag first', 'orange')
      return
    }
    setCart((current) => {
      const customizationKey = customizations.join('|')
      const existing = current.find((item) => item.id === food.id && (item.customizations ?? []).join('|') === customizationKey)
      if (existing) {
        return current.map((item) => item === existing ? { ...item, quantity: item.quantity + quantity } : item)
      }
      return [...current, { ...food, quantity, note, customizations, customizationTotal, selectedOptions }]
    })
    showToast(`${food.name} is in your bag`, 'green')
  }

  const setItemQuantity = (id: string, quantity: number) => {
    setCart((current) => quantity <= 0 ? current.filter((item) => item.id !== id) : current.map((item) => item.id === id ? { ...item, quantity } : item))
  }

  const toggleFavorite = (id: string) => {
    const nextFavorite = !favorites.includes(id)
    setFavorites((current) => nextFavorite ? [...current, id] : current.filter((item) => item !== id))
    if (session && supabase && backendStatus === 'live') {
      void setFavorite(id, session.user.id, nextFavorite, supabase).catch((error) => {
        setFavorites((current) => nextFavorite ? current.filter((item) => item !== id) : [...current, id])
        showToast(error instanceof Error ? error.message : 'Could not update saved bites', 'orange')
      })
    }
    showToast(nextFavorite ? 'Saved for your next craving' : 'Removed from your saved bites', 'ink')
  }

  const openFood = (food: Food) => {
    setSelectedCustomizations([])
    setSelectedOptions([])
    setSelectedQuantity(1)
    navigate(`/food/${food.id}`)
  }

  const placeOrder = async () => {
    if (!cart.length) {
      showToast('Your bag is empty — go find a craving', 'orange')
      navigate('/')
      return
    }
    if (backendStatus === 'live') {
      if (!session) {
        showToast('Sign in to place a real order', 'orange')
        navigate('/profile')
        return
      }
      try {
        const liveOrder = await placeLiveOrder(session, cart, liveSlotId, supabase!)
        setOrder(liveOrder)
        setRemoteOrders((current) => [liveOrder, ...current.filter((item) => item.backendId !== liveOrder.backendId)])
        setCart([])
        showToast('Order locked in. Queue = officially not your problem.', 'green')
        navigate(`/orders/${liveOrder.id}`)
      } catch (error) {
        showToast(error instanceof Error ? error.message : 'Could not place the real order', 'orange')
      }
      return
    }
    const label = cart.map((item) => `${item.name} × ${item.quantity}`).join(' · ')
    setOrder({ ...demoOrder, itemLabel: label, total: cartTotal + fulfillmentFee(fulfillmentMode), pickup: `${fulfillmentMode === 'delivery' ? 'Delivery' : 'Pickup'} · ${checkoutSlot}`, counter: fulfillmentMode === 'delivery' ? 'Hostel H • Block 3' : demoOrder.counter, fulfillmentMode, status: 'Queued' })
    setCart([])
    showToast('Order locked in. Queue = officially not your problem.', 'green')
    navigate(`/orders/${demoOrder.id}`)
  }

  const handleRole = (nextRole: Role) => {
    setRole(nextRole)
    if (nextRole === 'vendor') navigate('/vendor')
    else if (nextRole === 'admin') navigate('/admin')
    else navigate('/')
  }

  const navItems = activeRole === 'student'
    ? [
        { label: 'Discover', path: '/', icon: '✦' },
        { label: 'My orders', path: '/orders', icon: '↗' },
        { label: 'Saved bites', path: '/favorites', icon: '♡' },
      ]
    : activeRole === 'vendor'
      ? [
          { label: 'Live board', path: '/vendor', icon: '▦' },
          { label: 'Menu', path: '/vendor/menu', icon: '✚' },
          { label: 'Insights', path: '/vendor/insights', icon: '↗' },
        ]
      : [
          { label: 'Overview', path: '/admin', icon: '▦' },
          { label: 'Outlets', path: '/admin/outlets', icon: '⌂' },
          { label: 'Users', path: '/admin/users', icon: '◎' },
        ]

  return (
    <div className="app-frame">
      <div className="grain" aria-hidden="true" />
      <Sidebar role={activeRole} navItems={navItems} path={path} navigate={navigate} onRole={handleRole} />
      <main className="workspace">
        <Topbar role={activeRole} path={path} navigate={navigate} cartCount={cartCount} search={search} onSearch={setSearch} backendStatus={backendStatus} session={session} />
        <div className="content-wrap">
          {activeRole === 'student' && page === 'home' && <HomePage foods={catalog.foods} outlets={catalog.outlets} categories={catalog.categories} search={search} activeCategory={activeCategory} setActiveCategory={setActiveCategory} openFood={openFood} navigate={navigate} addToCart={addToCart} favorites={favorites} toggleFavorite={toggleFavorite} backendStatus={backendStatus} />}
          {activeRole === 'student' && page === 'menu' && <MenuPage foods={catalog.foods} categories={catalog.categories} outlet={catalog.outlets.find((outlet) => outlet.id === segments[1])} openFood={openFood} navigate={navigate} addToCart={addToCart} favorites={favorites} toggleFavorite={toggleFavorite} />}
          {activeRole === 'student' && page === 'food' && <FoodDetailPage food={foodFromPath} outlet={catalog.outlets.find((outlet) => outlet.id === foodFromPath?.outletId)} navigate={navigate} addToCart={addToCart} selectedCustomizations={selectedCustomizations} setSelectedCustomizations={setSelectedCustomizations} selectedOptions={selectedOptions} setSelectedOptions={setSelectedOptions} selectedQuantity={selectedQuantity} setSelectedQuantity={setSelectedQuantity} />}
          {activeRole === 'student' && page === 'cart' && <CartPage cart={cart} cartTotal={cartTotal} fulfillmentMode={fulfillmentMode} setItemQuantity={setItemQuantity} navigate={navigate} />}
          {activeRole === 'student' && page === 'checkout' && <CheckoutPage cart={cart} cartTotal={cartTotal} fulfillmentMode={fulfillmentMode} setFulfillmentMode={setFulfillmentMode} slot={checkoutSlot} setSlot={setCheckoutSlot} pickupSlots={pickupSlots} liveMode={backendStatus === 'live'} liveSlotId={liveSlotId} setLiveSlotId={setLiveSlotId} navigate={navigate} placeOrder={placeOrder} />}
          {activeRole === 'student' && page === 'orders' && !segments[1] && <OrdersPage foods={catalog.foods} order={order} navigate={navigate} addToCart={addToCart} />}
          {activeRole === 'student' && page === 'orders' && Boolean(segments[1]) && <OrderTrackingPage order={order} navigate={navigate} />}
          {activeRole === 'student' && page === 'favorites' && <FavoritesPage foods={catalog.foods} favorites={favorites} openFood={openFood} navigate={navigate} addToCart={addToCart} toggleFavorite={toggleFavorite} />}
          {activeRole === 'student' && page === 'profile' && <ProfilePage session={session} backendStatus={backendStatus} backendError={backendError} onRole={handleRole} onSignOut={() => { void supabase?.auth.signOut() }} />}
          {activeRole === 'vendor' && page.startsWith('vendor') && <VendorPage foods={catalog.foods} navigate={navigate} onRole={handleRole} />}
          {activeRole === 'admin' && page.startsWith('admin') && <AdminPage outlets={catalog.outlets} navigate={navigate} onRole={handleRole} />}
          {activeRole === 'student' && page !== 'home' && page !== 'menu' && page !== 'food' && page !== 'cart' && page !== 'checkout' && page !== 'orders' && page !== 'favorites' && page !== 'profile' && <EmptyState title="That page took a snack break" copy="Let’s get you back to the good stuff." action="Back to discovery" onAction={() => navigate('/')} />}
        </div>
      </main>
      {activeRole === 'student' && <CartRail cart={cart} total={cartTotal} count={cartCount} navigate={navigate} setItemQuantity={setItemQuantity} order={order} />}
      <MobileNav role={activeRole} path={path} navigate={navigate} cartCount={cartCount} />
      {toast && <div className={`toast toast-${toast.tone}`}><span className="toast-dot" />{toast.message}</div>}
    </div>
  )
}

function BrandMark({ compact = false }: { compact?: boolean }) {
  return <div className={`brand-mark ${compact ? 'brand-compact' : ''}`}><span className="brand-glyph">C</span><span className="brand-word">RAVE</span><span className="brand-spark">✦</span></div>
}

function Sidebar({ role, navItems, path, navigate, onRole }: { role: Role; navItems: { label: string; path: string; icon: string }[]; path: string; navigate: (path: string) => void; onRole: (role: Role) => void }) {
  return <aside className="sidebar">
    <button className="brand-button" onClick={() => navigate('/')} aria-label="Go to Crave home"><BrandMark /></button>
    <div className="campus-chip"><span className="pulse-dot" /> SRMIST <span className="chip-arrow">⌄</span></div>
    <div className="sidebar-rule" />
    <p className="eyebrow sidebar-label">{role === 'student' ? 'Your campus' : `${role} mode`}</p>
    <nav className="side-nav">
      {navItems.map((item) => <button key={item.path} className={`side-nav-item ${isActivePath(path, item.path) ? 'nav-active' : ''}`} onClick={() => navigate(item.path)}><span className="nav-icon">{item.icon}</span><span>{item.label}</span>{isActivePath(path, item.path) && <span className="nav-notch" />}</button>)}
    </nav>
    <div className="sidebar-spacer" />
    {role === 'student' && <div className="sidebar-order-note" onClick={() => navigate('/orders/CRV-4821')}><span className="order-note-icon">↗</span><div><span className="eyebrow">Current order</span><strong>Ready in 12 min</strong></div><span className="tiny-arrow">→</span></div>}
    <div className="profile-mini" onClick={() => role === 'student' && navigate('/profile')}><div className="avatar">DS</div><div><strong>Dhruv Shah</strong><span>Student · B.Tech CSE</span></div><button className="more-button" onClick={(event) => { event.stopPropagation(); onRole(role === 'student' ? 'vendor' : role === 'vendor' ? 'admin' : 'student') }}>•••</button></div>
  </aside>
}

function Topbar({ role, path, navigate, cartCount, search, onSearch, backendStatus, session }: { role: Role; path: string; navigate: (path: string) => void; cartCount: number; search: string; onSearch: (value: string) => void; backendStatus: 'loading' | 'live' | 'fallback'; session: Session | null }) {
  return <header className="topbar">
    <div className="mobile-brand"><BrandMark compact /></div>
    <div className="breadcrumbs"><span>Crave</span><span className="crumb-slash">/</span><strong>{role === 'student' ? prettyPage(path) : `${role[0].toUpperCase()}${role.slice(1)} workspace`}</strong></div>
    <div className="top-actions">
      <label className="top-search"><span>⌕</span><input value={search} onChange={(event) => onSearch(event.target.value)} placeholder="Search a craving..." /><kbd>⌘ K</kbd></label>
      <button className="icon-button notification-button" onClick={() => navigate('/orders')} aria-label="Open notifications">◌<span className="notification-dot" /></button>
      <button className={`backend-pill backend-${backendStatus}`} onClick={() => navigate('/profile')} title={session ? 'Signed in to Supabase' : 'Open your Crave ID'}><span />{backendStatus === 'live' ? 'LIVE' : backendStatus === 'loading' ? 'SYNC' : 'DEMO'}{session ? ' · YOU' : ''}</button>
      {role === 'student' && <button className="cart-pill" onClick={() => navigate('/cart')}><span>Bag</span><strong>{cartCount || '0'}</strong><span className="cart-arrow">↗</span></button>}
    </div>
  </header>
}

function MobileNav({ role, path, navigate, cartCount }: { role: Role; path: string; navigate: (path: string) => void; cartCount: number }) {
  const items = role === 'student' ? [{ label: 'Home', path: '/' }, { label: 'Orders', path: '/orders' }, { label: 'Saved', path: '/favorites' }, { label: 'Profile', path: '/profile' }] : [{ label: 'Board', path: role === 'vendor' ? '/vendor' : '/admin' }, { label: 'Menu', path: role === 'vendor' ? '/vendor/menu' : '/admin/outlets' }, { label: 'Profile', path: '/' }]
  return <nav className="mobile-nav">{items.map((item) => <button key={item.label} className={isActivePath(path, item.path) ? 'mobile-active' : ''} onClick={() => navigate(item.path)}><span className="mobile-icon">{item.label === 'Home' ? '✦' : item.label === 'Orders' || item.label === 'Board' ? '↗' : item.label === 'Saved' ? '♡' : item.label === 'Menu' ? '✚' : '◎'}</span><span>{item.label}</span>{item.label === 'Home' && cartCount > 0 && <b className="mobile-badge">{cartCount}</b>}</button>)}</nav>
}

function HomePage({ foods, outlets, categories, search, activeCategory, setActiveCategory, openFood, navigate, addToCart, favorites, toggleFavorite, backendStatus }: { foods: Food[]; outlets: Outlet[]; categories: Category[]; search: string; activeCategory: string; setActiveCategory: (category: string) => void; openFood: (food: Food) => void; navigate: (path: string) => void; addToCart: (food: Food, quantity?: number) => void; favorites: string[]; toggleFavorite: (id: string) => void; backendStatus: 'loading' | 'live' | 'fallback' }) {
  const filteredFoods = useMemo(() => foods.filter((food) => (activeCategory === 'all' || food.category === activeCategory) && `${food.name} ${food.description}`.toLowerCase().includes(search.toLowerCase())), [activeCategory, foods, search])
  const primaryOutletId = outlets[0]?.id || 'nosh'
  return <div className="page page-home">
    <div className="home-intro">
      <div><span className="eyebrow orange-ink">MONDAY, 04 OCTOBER 2026 · 12:18 PM</span><h1>Lunch break?<br /><em>Make it count.</em></h1><p className="lede">Good food, zero queue, more time to pretend you’re not procrastinating.</p></div>
      <div className="intro-sticker sticker-tilt">{backendStatus === 'live' ? 'LIVE' : 'OPEN'}<br /><strong>{backendStatus === 'live' ? 'MENU' : 'NOW'}</strong><span>{backendStatus === 'live' ? 'from Supabase' : 'campus wide'}</span></div>
    </div>
    <section className="hero-card">
      <div className="hero-copy"><span className="sticker sticker-dark">TODAY’S MOOD</span><h2>Craving something<br /><span>good?</span></h2><p>Live campus food, synced from your Crave backend and ready before your next lecture.</p><button className="button button-light" onClick={() => foods[0] && openFood(foods[0])} disabled={!foods[0]}>Explore the menu <span>↗</span></button></div>
      <div className="hero-art"><div className="hero-orbit orbit-one" /><div className="hero-orbit orbit-two" /><div className="hero-food-emoji">🥪</div><span className="hero-stamp">9.8<br /><small>CRAVE<br />RATING</small></span><span className="hero-doodle doodle-one">yum!</span><span className="hero-doodle doodle-two">12 min</span></div>
    </section>
    <section className="section-block category-section"><div className="section-heading"><div><span className="eyebrow">START HERE</span><h2>What’s the vibe?</h2></div><button className="text-button" onClick={() => { setActiveCategory('all'); navigate(`/menu/${primaryOutletId}`) }}>see all eats <span>↗</span></button></div><div className="category-row">{categories.map((category) => <button key={category.id} className={`category-chip ${activeCategory === category.id ? 'category-selected' : ''}`} onClick={() => setActiveCategory(category.id)}><span>{category.emoji}</span>{category.label}</button>)}</div></section>
    <section className="section-block"><div className="section-heading"><div><span className="eyebrow">AROUND CAMPUS</span><h2>Open around you <span className="heading-spark">✦</span></h2></div><button className="text-button" onClick={() => navigate(`/menu/${primaryOutletId}`)}>menu view <span>↗</span></button></div><div className="outlet-grid">{outlets.filter((outlet) => outlet.open).slice(0, 4).map((outlet, index) => <OutletCard key={outlet.id} outlet={outlet} featured={index === 0} navigate={navigate} />)}</div></section>
    <section className="section-block bites-block"><div className="section-heading"><div><span className="eyebrow">FOR YOU, OBVIOUSLY</span><h2>Bite-sized picks</h2></div><div className="tiny-note"><span className="tiny-scribble">↳</span> based on your <strong>very good</strong> taste</div></div><div className="food-grid">{filteredFoods.slice(0, 4).map((food) => <FoodCard key={food.id} food={food} openFood={openFood} addToCart={addToCart} favorite={favorites.includes(food.id)} toggleFavorite={toggleFavorite} />)}</div></section>
    <section className="campus-banner"><div className="banner-scribble">✷</div><div><span className="eyebrow">THE CRAVE PROMISE</span><h2>Less queue. More <em>you.</em></h2></div><p>Pick a slot, grab your token, get back to your people before someone asks for your notes.</p><span className="banner-arrow">→</span></section>
  </div>
}

function OutletCard({ outlet, featured, navigate }: { outlet: Outlet; featured?: boolean; navigate: (path: string) => void }) {
  return <button className={`outlet-card tone-${outlet.tone} ${featured ? 'outlet-featured' : ''}`} onClick={() => navigate(`/menu/${outlet.id}`)}><div className="outlet-art"><span className="outlet-emoji">{outlet.emoji}</span><span className="outlet-open">{outlet.open ? 'OPEN' : 'CLOSED'}</span><span className="outlet-wiggle">↗</span></div><div className="outlet-card-body"><div><h3>{outlet.name}</h3><p>{outlet.location}</p></div><div className="outlet-meta"><span>★ {outlet.rating}</span><span>{outlet.eta}</span></div></div>{featured && <span className="outlet-sticker">MOST<br />LOVED</span>}</button>
}

function FoodCard({ food, openFood, addToCart, favorite, toggleFavorite }: { food: Food; openFood: (food: Food) => void; addToCart: (food: Food) => void; favorite: boolean; toggleFavorite: (id: string) => void }) {
  return <article className={`food-card tone-${food.tone}`} onClick={() => openFood(food)}><div className="food-image"><span className="food-emoji">{food.emoji}</span><span className="food-tag">{food.tag || food.category.replace('-', ' ')}</span><button className={`heart-button ${favorite ? 'is-favorite' : ''}`} onClick={(event) => { event.stopPropagation(); toggleFavorite(food.id) }} aria-label="Save food">{favorite ? '♥' : '♡'}</button><span className="food-dots">•••</span></div><div className="food-card-body"><div><h3>{food.name}</h3><p>{food.description}</p></div><div className="food-card-footer"><strong>{money(food.price)}</strong><span className="food-rating">★ {food.rating}</span><button className="add-button" onClick={(event) => { event.stopPropagation(); addToCart(food) }}>+</button></div></div></article>
}

function MenuPage({ foods, categories, outlet, openFood, navigate, addToCart, favorites, toggleFavorite }: { foods: Food[]; categories: Category[]; outlet?: Outlet; openFood: (food: Food) => void; navigate: (path: string) => void; addToCart: (food: Food) => void; favorites: string[]; toggleFavorite: (id: string) => void }) {
  const actualOutlet = outlet || seedOutlets[0]
  const menu = foods.filter((food) => food.outletId === actualOutlet.id)
  return <div className="page page-menu"><button className="back-link" onClick={() => navigate('/')}><span>←</span> back to discovery</button><section className={`menu-cover tone-${actualOutlet.tone}`}><div><span className="sticker sticker-dark">{actualOutlet.open ? 'OPEN NOW' : 'BACK SOON'}</span><h1>{actualOutlet.name}</h1><p>{actualOutlet.vibe}</p><div className="menu-cover-meta"><span>★ {actualOutlet.rating}</span><span>{actualOutlet.location}</span><span>⏱ {actualOutlet.eta}</span></div></div><div className="menu-hero-emoji">{actualOutlet.emoji}</div></section><div className="menu-toolbar"><div><span className="eyebrow">THE MENU</span><h2>Pick your <em>thing.</em></h2></div><div className="menu-filter-row">{categories.filter((category) => category.id !== 'all').map((category) => <button key={category.id} className="mini-filter" onClick={() => document.getElementById(category.id)?.scrollIntoView({ behavior: 'smooth' })}>{category.emoji} {category.label}</button>)}</div></div>{menu.length ? categories.filter((category) => category.id !== 'all').map((category) => { const items = menu.filter((food) => food.category === category.id); return items.length ? <section key={category.id} id={category.id} className="menu-category"><div className="category-title"><span className="category-number">0{categories.indexOf(category)}</span><h2>{category.label}</h2><span className="category-line" /></div><div className="food-grid">{items.map((food) => <FoodCard key={food.id} food={food} openFood={openFood} addToCart={addToCart} favorite={favorites.includes(food.id)} toggleFavorite={toggleFavorite} />)}</div></section> : null }) : <EmptyState title="This menu is taking a nap" copy="Try another outlet — campus cravings wait for nobody." />}</div>
}

function FoodDetailPage({ food, outlet, navigate, addToCart, selectedCustomizations, setSelectedCustomizations, selectedOptions, setSelectedOptions, selectedQuantity, setSelectedQuantity }: { food?: Food; outlet?: Outlet; navigate: (path: string) => void; addToCart: (food: Food, quantity?: number, note?: string, customizationTotal?: number, customizations?: string[], selectedOptions?: CustomizationOption[]) => void; selectedCustomizations: string[]; setSelectedCustomizations: (items: string[]) => void; selectedOptions: CustomizationOption[]; setSelectedOptions: (items: CustomizationOption[]) => void; selectedQuantity: number; setSelectedQuantity: (quantity: number) => void }) {
  if (!food) return <EmptyState title="That bite disappeared" copy="Let’s get you back to the good stuff." action="Back to discovery" onAction={() => navigate('/')} />
  const toggle = (item: string) => setSelectedCustomizations(selectedCustomizations.includes(item) ? selectedCustomizations.filter((value) => value !== item) : [...selectedCustomizations, item])
  const toggleOption = (option: CustomizationOption) => {
    const group = food.customizationGroups?.find((variant) => variant.id === option.variantId)
    const inGroup = selectedOptions.some((item) => item.id === option.id)
    const withoutGroup = selectedOptions.filter((item) => item.variantId !== option.variantId)
    const next = inGroup ? withoutGroup : group && group.maxSelections === 1 ? [...withoutGroup, option] : [...selectedOptions, option]
    setSelectedOptions(next)
    setSelectedCustomizations(next.map((item) => item.name))
  }
  const customizationTotal = selectedOptions.length ? selectedOptions.reduce((sum, item) => sum + item.extraPrice, 0) : selectedCustomizations.reduce((sum, item) => sum + getCustomizationPrice(item), 0)
  const total = (food.price + customizationTotal) * selectedQuantity
  return <div className="page page-food-detail"><button className="back-link" onClick={() => navigate(`/menu/${food.outletId}`)}><span>←</span> back to {outlet?.name}</button><div className="detail-layout"><div className={`detail-art tone-${food.tone}`}><span className="detail-emoji">{food.emoji}</span><span className="detail-loop loop-one" /><span className="detail-loop loop-two" /><span className="detail-note">made for<br /><strong>your break</strong></span></div><div className="detail-copy"><div className="detail-kicker"><span className="sticker sticker-orange">{food.tag || 'CAMPUS CLASSIC'}</span><span className="detail-rating">★ {food.rating} · {food.prep}</span></div><h1>{food.name}</h1><p className="detail-description">{food.description}</p><div className="detail-divider" /><div className="detail-choice"><div className="choice-heading"><span>MAKE IT YOURS</span><small>tap to add</small></div><div className="customization-list">{food.customizationGroups?.length ? food.customizationGroups.map((group) => <div className="customization-group" key={group.id}><span className="customization-group-label">{group.name}{group.isRequired ? ' · required' : ''}</span>{group.options.map((option) => <button key={option.id} className={`customization ${selectedOptions.some((item) => item.id === option.id) ? 'customization-selected' : ''}`} onClick={() => toggleOption(option)}><span className="customization-check">{selectedOptions.some((item) => item.id === option.id) ? '✓' : '+'}</span>{option.name}<strong>{option.extraPrice ? `+ ₹${option.extraPrice}` : ''}</strong></button>)}</div>) : food.customizations?.map((item) => <button key={item} className={`customization ${selectedCustomizations.includes(item) ? 'customization-selected' : ''}`} onClick={() => toggle(item)}><span className="customization-check">{selectedCustomizations.includes(item) ? '✓' : '+'}</span>{item}<strong>{getCustomizationPrice(item) ? `+ ₹${getCustomizationPrice(item)}` : ''}</strong></button>)}</div></div><div className="detail-actions"><div className="quantity-control"><button onClick={() => setSelectedQuantity(Math.max(1, selectedQuantity - 1))}>−</button><strong>{selectedQuantity}</strong><button onClick={() => setSelectedQuantity(selectedQuantity + 1)}>+</button></div><button className="button button-primary add-detail" onClick={() => { addToCart(food, selectedQuantity, selectedCustomizations.join(', '), customizationTotal, selectedCustomizations, selectedOptions); navigate('/cart') }}>Add to bag <span>{money(total)} ↗</span></button></div><p className="detail-footnote">Pickup from <strong>{outlet?.name}</strong> · {outlet?.eta}</p></div></div></div>
}

function CartPage({ cart, cartTotal, fulfillmentMode, setItemQuantity, navigate }: { cart: CartItem[]; cartTotal: number; fulfillmentMode: FulfillmentMode; setItemQuantity: (id: string, quantity: number) => void; navigate: (path: string) => void }) {
  return <div className="page page-cart"><div className="page-heading-row"><div><span className="eyebrow">YOUR BAG</span><h1>Good choices<br /><em>live here.</em></h1></div><span className="bag-count">{cart.reduce((sum, item) => sum + item.quantity, 0)} items</span></div>{cart.length ? <div className="cart-layout"><div className="cart-list">{cart.map((item) => <CartItemRow key={`${item.id}-${item.customizations?.join('-') || 'plain'}`} item={item} setItemQuantity={setItemQuantity} />)}<button className="add-more" onClick={() => navigate('/')}><span>+</span> Add something else</button></div><CheckoutSummary cartTotal={cartTotal} fulfillmentMode={fulfillmentMode} itemCount={cart.reduce((sum, item) => sum + item.quantity, 0)} navigate={navigate} /></div> : <EmptyState title="Your bag is feeling shy" copy="Find something crispy, saucy, or suspiciously sweet." action="Go find a craving" onAction={() => navigate('/')} />}</div>
}

function CartItemRow({ item, setItemQuantity }: { item: CartItem; setItemQuantity: (id: string, quantity: number) => void }) {
  return <div className="cart-item-row"><div className={`cart-item-art tone-${item.tone}`}><span>{item.emoji}</span></div><div className="cart-item-copy"><div><h3>{item.name}</h3><p>{item.note || item.description}</p></div><strong>{money(cartLineTotal(item))}</strong><div className="row-controls"><button onClick={() => setItemQuantity(item.id, item.quantity - 1)}>−</button><span>{item.quantity}</span><button onClick={() => setItemQuantity(item.id, item.quantity + 1)}>+</button></div></div></div>
}

function CheckoutSummary({ cartTotal, fulfillmentMode, itemCount, navigate }: { cartTotal: number; fulfillmentMode: FulfillmentMode; itemCount: number; navigate: (path: string) => void }) {
  const fee = fulfillmentFee(fulfillmentMode)
  return <div className="checkout-summary"><span className="eyebrow">ORDER TOTAL</span><div className="summary-line"><span>Items · {itemCount}</span><strong>{money(cartTotal)}</strong></div><div className="summary-line"><span>{fulfillmentMode === 'delivery' ? 'Delivery fee' : 'Campus fee'}</span><strong>{money(fee)}</strong></div><div className="summary-line summary-total"><span>Total</span><strong>{money(cartTotal + fee)}</strong></div><button className="button button-primary full-button" onClick={() => navigate('/checkout')}>Choose pickup or delivery <span>↗</span></button><p className="summary-note">No surprise fees. Just surprisingly good food.</p></div>
}

function CheckoutPage({ cart, cartTotal, fulfillmentMode, setFulfillmentMode, slot, setSlot, pickupSlots, liveMode, liveSlotId, setLiveSlotId, navigate, placeOrder }: { cart: CartItem[]; cartTotal: number; fulfillmentMode: FulfillmentMode; setFulfillmentMode: (mode: FulfillmentMode) => void; slot: string; setSlot: (slot: string) => void; pickupSlots: LivePickupSlot[]; liveMode: boolean; liveSlotId: string; setLiveSlotId: (id: string) => void; navigate: (path: string) => void; placeOrder: () => void }) {
  const staticSlots = ['12:40 PM', '12:55 PM', '1:10 PM', '1:25 PM']
  const slots = liveMode && pickupSlots.length ? pickupSlots.map((pickupSlot) => pickupSlot.label) : staticSlots
  const fee = liveMode ? Math.round(cartTotal * 0.05) : fulfillmentFee(fulfillmentMode)
  const isDelivery = !liveMode && fulfillmentMode === 'delivery'
  const selectSlot = (value: string, index: number) => { setSlot(value); if (liveMode) setLiveSlotId(pickupSlots[index]?.id || '') }
  return <div className="page page-checkout"><button className="back-link" onClick={() => navigate('/cart')}><span>←</span> back to bag</button><div className="checkout-heading"><span className="eyebrow">LAST LAP</span><h1>Almost yours.<br /><em>Pick a moment.</em></h1></div><div className="checkout-layout"><div className="checkout-main"><div className="checkout-step"><div className="step-number">01</div><div className="step-copy"><span className="eyebrow">HOW SHOULD IT ARRIVE?</span><h2>Pick your kind of convenient.</h2><div className="fulfillment-grid"><button className={`fulfillment-option ${!isDelivery ? 'fulfillment-selected' : ''}`} onClick={() => setFulfillmentMode('pickup')}><span className="fulfillment-icon">⌖</span><strong>Pick it up</strong><small>Skip the queue at Nosh Lab</small></button><button className={`fulfillment-option ${isDelivery ? 'fulfillment-selected' : ''}`} onClick={() => setFulfillmentMode('delivery')} disabled={liveMode}><span className="fulfillment-icon">↗</span><strong>Drop it here</strong><small>Hostel H delivery · +₹45</small></button></div></div></div><div className="checkout-step"><div className="step-number">02</div><div className="step-copy"><span className="eyebrow">{isDelivery ? 'DROP-OFF SPOT' : 'PICKUP SPOT'}</span><h2>{isDelivery ? 'Where should it land?' : 'Where should we meet?'}</h2><button className="pickup-select"><span className="pickup-pin">{isDelivery ? '↗' : '⌖'}</span><span><strong>{isDelivery ? 'Hostel H · Block 3' : 'Nosh Lab'}</strong><small>{isDelivery ? 'Hostel H · next to the common room' : 'Tech Park · Ground floor'}</small></span><span className="select-caret">⌄</span></button></div></div><div className="checkout-step"><div className="step-number">03</div><div className="step-copy"><span className="eyebrow">{isDelivery ? 'DELIVERY SLOT' : 'PICKUP SLOT'}</span><h2>When are you free?</h2><div className="slot-grid">{slots.map((value, index) => <button key={value} className={`slot-button ${slot === value ? 'slot-selected' : ''}`} onClick={() => selectSlot(value, index)}><strong>{value}</strong><small>{index === 0 ? 'fastest' : index === 1 ? 'popular' : 'still chill'}</small></button>)}</div></div></div><div className="checkout-step checkout-payment"><div className="step-number">04</div><div className="step-copy"><span className="eyebrow">PAYMENT</span><h2>{liveMode ? 'Pay at the counter, no queue theatre.' : 'Demo mode, good vibes.'}</h2><div className="payment-card"><span className="payment-icon">↗</span><span><strong>Campus wallet</strong><small>•••• 4242 · ready to pretend</small></span><span className="payment-check">✓</span></div></div></div></div><aside className="checkout-aside"><span className="eyebrow">YOUR ORDER</span><div className="checkout-items">{cart.map((item) => <div className="checkout-item" key={`${item.id}-${item.customizations?.join('-') || 'plain'}`}><span>{item.emoji}</span><div><strong>{item.name}</strong><small>{item.quantity} × {money(item.price + (item.customizationTotal ?? 0))}{item.note ? ` · ${item.note}` : ''}</small></div><b>{money(cartLineTotal(item))}</b></div>)}</div><div className="summary-line"><span>Food subtotal</span><strong>{money(cartTotal)}</strong></div><div className="summary-line"><span>{isDelivery ? 'Delivery fee' : 'Campus fee'}</span><strong>{money(fee)}</strong></div><div className="summary-line summary-total"><span>To pay</span><strong>{money(cartTotal + fee)}</strong></div><button className="button button-primary full-button" onClick={placeOrder} disabled={liveMode && (!liveSlotId || !cart.length)}>Place {liveMode ? 'real pickup order' : 'demo order'} <span>↗</span></button><p className="summary-note">{liveMode ? 'Supabase validates stock, pricing, and slot capacity.' : 'By tapping, you agree this is a very real demo.'}</p></aside></div></div>
}

function OrdersPage({ foods, order, navigate, addToCart }: { foods: Food[]; order: typeof demoOrder; navigate: (path: string) => void; addToCart: (food: Food, quantity?: number) => void }) {
  return <div className="page page-orders"><div className="page-heading-row"><div><span className="eyebrow">THE RECEIPTS</span><h1>Your order<br /><em>story.</em></h1></div><button className="button button-dark" onClick={() => navigate('/')}>new craving <span>↗</span></button></div><section className="active-order-card"><div className="active-order-top"><div><span className="sticker sticker-orange">ON THE WAY TO YOU</span><h2>{order.outlet}</h2><p>{order.itemLabel}</p></div><div className="order-id">#{order.id}</div></div><div className="order-progress"><div className="progress-line"><span className="progress-fill" style={{ width: order.status === 'Queued' ? '28%' : order.status === 'Cooking' ? '66%' : '100%' }} /></div><div className="progress-steps"><span className="step-done">Queued</span><span className={order.status !== 'Queued' ? 'step-done' : ''}>Cooking</span><span className={order.status === 'Ready for pickup' ? 'step-done' : ''}>Ready for pickup</span></div></div><div className="active-order-footer"><div><span className="eyebrow">PICKUP SLOT</span><strong>{order.pickup}</strong></div><div><span className="eyebrow">TOTAL</span><strong>{money(order.total)}</strong></div><button className="button button-light small-button" onClick={() => navigate(`/orders/${order.id}`)}>track order <span>↗</span></button></div></section><section className="section-block order-history"><div className="section-heading"><div><span className="eyebrow">BEFORE THAT</span><h2>Good times, documented.</h2></div></div><div className="history-list">{pastOrders.map((past) => <div className="history-row" key={past.id}><div className="history-thumb">{past.outlet === 'Dosa District' ? '🥞' : '🍜'}</div><div className="history-copy"><strong>{past.outlet}</strong><p>{past.itemLabel}</p></div><span className="history-date">{past.date}</span><strong className="history-total">{money(past.total)}</strong><button className="history-reorder" onClick={() => { const food = foods.find((item) => past.itemLabel.includes(item.name)); if (food) addToCart(food, past.itemLabel.includes('× 2') ? 2 : 1) }}>reorder ↗</button></div>)}</div></section></div>
}

function OrderTrackingPage({ order, navigate }: { order: typeof demoOrder; navigate: (path: string) => void }) {
  return <div className="page page-tracking"><button className="back-link" onClick={() => navigate('/orders')}><span>←</span> all orders</button><div className="tracking-head"><div><span className="eyebrow">LIVE ORDER · #{order.id}</span><h1>It’s getting<br /><em>delicious.</em></h1></div><span className="tracking-status">{order.status === 'Queued' ? 'QUEUED' : 'COOKING'} <i /></span></div><div className="tracking-layout"><section className="tracking-card"><div className="tracking-card-top"><div><span className="sticker sticker-dark">PICKUP TOKEN</span><h2>Show this at the counter</h2><p>{order.counter}</p></div><span className="tracking-arrow">↗</span></div><div className="qr-wrap"><FakeQr /><div className="qr-label">{order.id} · {order.pickup}</div></div><div className="tracking-tip"><span>✦</span> Your phone is your token. No printing. No queue theatre.</div></section><section className="status-card"><span className="eyebrow">LIVE STATUS</span><div className="status-list"><StatusRow label="Order received" time="12:18 PM" done /><StatusRow label="Kitchen is cooking" time="12:24 PM" done={order.status !== 'Queued'} active={order.status === 'Cooking'} /><StatusRow label="Ready for pickup" time="~12:36 PM" done={order.status === 'Ready for pickup'} active={order.status === 'Ready for pickup'} /><StatusRow label="You, reunited with food" time={order.pickup} /></div><div className="status-bottom"><span>Pickup from</span><strong>{order.outlet} · {order.pickup}</strong></div></section></div><div className="tracking-foot"><span className="eyebrow">NEED A HAND?</span><button className="text-button">message support <span>↗</span></button><span className="tracking-foot-copy">We answer faster than campus wifi sometimes.</span></div></div>
}

function StatusRow({ label, time, done, active }: { label: string; time: string; done?: boolean; active?: boolean }) {
  return <div className={`status-row ${done ? 'status-done' : ''} ${active ? 'status-active' : ''}`}><span className="status-icon">{done ? '✓' : active ? '•' : '○'}</span><div><strong>{label}</strong><small>{time}</small></div>{active && <span className="status-live">LIVE</span>}</div>
}

function FakeQr() {
  const pattern = [1,1,1,0,1,0,1,1,1,1,0,0,1,1,0,1,0,1,0,1,1,0,1,0,0,1,1,1,0,0,1,1,0,1,1,0,0,1,1,0,1,0,1,1,0,1,1,1,0,0,1,0,1,0,1,1,0,1,1,0,0,1,1,1,0,1,0,1,1,0,1,0,0,1,1,0,1,1,1,0,0,1]
  return <div className="fake-qr" aria-label="Demo QR pickup token">{pattern.map((filled, index) => <span key={index} className={filled ? 'qr-on' : ''} />)}</div>
}

function FavoritesPage({ foods, favorites, openFood, navigate, addToCart, toggleFavorite }: { foods: Food[]; favorites: string[]; openFood: (food: Food) => void; navigate: (path: string) => void; addToCart: (food: Food) => void; toggleFavorite: (id: string) => void }) {
  const savedFoods = foods.filter((food) => favorites.includes(food.id))
  return <div className="page page-favorites"><div className="page-heading-row"><div><span className="eyebrow">YOUR LITTLE BLACK BOOK</span><h1>Saved<br /><em>bites.</em></h1></div><span className="heart-burst">♥</span></div><p className="lede favorites-lede">The things you said “I’ll get this later” about. Later is now.</p>{savedFoods.length ? <div className="food-grid favorites-grid">{savedFoods.map((food) => <FoodCard key={food.id} food={food} openFood={openFood} addToCart={addToCart} favorite toggleFavorite={toggleFavorite} />)}</div> : <EmptyState title="Nothing saved yet" copy="Tap the little heart on a food card when the vibes are right." action="Browse the menu" onAction={() => navigate('/')} />}</div>
}

function ProfilePage({ session, backendStatus, backendError, onRole, onSignOut }: { session: Session | null; backendStatus: 'loading' | 'live' | 'fallback'; backendError: string; onRole: (role: Role) => void; onSignOut: () => void }) {
  const [authMode, setAuthMode] = useState<'sign-in' | 'sign-up'>('sign-in')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [name, setName] = useState('')
  const [authMessage, setAuthMessage] = useState('')
  const [authBusy, setAuthBusy] = useState(false)

  const submitAuth = async () => {
    if (!supabase) {
      setAuthMessage('Live Supabase is not configured in this environment.')
      return
    }
    setAuthBusy(true)
    setAuthMessage('')
    const result = authMode === 'sign-in'
      ? await supabase.auth.signInWithPassword({ email, password })
      : await supabase.auth.signUp({ email, password, options: { data: { name } } })
    setAuthBusy(false)
    if (result.error) setAuthMessage(result.error.message)
    else setAuthMessage(authMode === 'sign-up' ? 'Check your email to confirm your Crave ID.' : 'You are back in the campus food loop.')
  }

  return <div className="page page-profile"><span className="eyebrow">YOUR CRAVE ID</span><h1>Hi, <em>{session?.user.email?.split('@')[0] || 'Dhruv'}.</em></h1><div className="profile-hero"><div className="profile-avatar-large">DS</div><div><span className="sticker sticker-orange">{session ? 'CONNECTED' : 'STUDENT'}</span><h2>{session?.user.email || 'Dhruv Shah'}</h2><p>{session ? 'Authenticated with Supabase · RLS protected' : 'B.Tech CSE · 3rd year · SRMIST'}</p></div>{session ? <button className="button button-dark" onClick={onSignOut}>sign out <span>↗</span></button> : <button className="button button-dark" onClick={() => document.getElementById('crave-auth')?.scrollIntoView({ behavior: 'smooth' })}>connect ID <span>↗</span></button>}</div><div className="profile-grid"><div className="profile-stat"><span>{session ? 'LIVE' : backendStatus === 'live' ? 'SYNC' : 'DEMO'}</span><small>backend mode</small></div><div className="profile-stat"><span>{session ? 'RLS' : '—'}</span><small>data access</small></div><div className="profile-stat"><span>{session ? 'ON' : 'OFF'}</span><small>real orders</small></div></div>{!session && <section id="crave-auth" className="auth-card"><div><span className="eyebrow">{authMode === 'sign-in' ? 'WELCOME BACK' : 'NEW CRAVE ID'}</span><h2>{authMode === 'sign-in' ? 'Sign in. Skip queues.' : 'Make the campus yours.'}</h2><p>Connect your Supabase account to save bites, sync your cart, and place real pickup orders.</p></div><div className="auth-form">{authMode === 'sign-up' && <input value={name} onChange={(event) => setName(event.target.value)} placeholder="Your name" aria-label="Your name" />}{authMode === 'sign-up' && <input value={email} onChange={(event) => setEmail(event.target.value)} placeholder="College email" aria-label="College email" type="email" />}{authMode === 'sign-in' && <input value={email} onChange={(event) => setEmail(event.target.value)} placeholder="College email" aria-label="College email" type="email" />}<input value={password} onChange={(event) => setPassword(event.target.value)} placeholder="Password" aria-label="Password" type="password" /><button className="button button-primary" onClick={() => void submitAuth()} disabled={authBusy}>{authBusy ? 'connecting…' : authMode === 'sign-in' ? 'sign in ↗' : 'create ID ↗'}</button>{authMessage && <small className="auth-message">{authMessage}</small>}<button className="text-button" onClick={() => { setAuthMode(authMode === 'sign-in' ? 'sign-up' : 'sign-in'); setAuthMessage('') }}>{authMode === 'sign-in' ? 'Need a Crave ID? create one' : 'Already have a Crave ID? sign in'}</button></div></section>}{backendError && <p className="backend-error">Live sync note: {backendError}</p>}<section className="role-switcher"><span className="eyebrow">DEMO SWITCHER</span><h2>Peek behind the counter</h2><p>Try the vendor or admin view to see how Crave works for the people making the magic happen.</p><div className="role-buttons"><button onClick={() => onRole('student')}>Student view <span>✓</span></button><button onClick={() => onRole('vendor')}>Vendor view <span>↗</span></button><button onClick={() => onRole('admin')}>Admin view <span>↗</span></button></div></section></div>
}
function VendorPage({ foods, navigate, onRole }: { foods: Food[]; navigate: (path: string) => void; onRole: (role: Role) => void }) {
  return <div className="page page-ops"><div className="ops-heading"><div><span className="eyebrow orange-ink">VENDOR CONSOLE · NOSH LAB</span><h1>Make lunch<br /><em>move.</em></h1></div><div className="ops-status"><span className="pulse-dot" /> accepting orders <button onClick={() => onRole('student')}>exit demo ↗</button></div></div><div className="metric-row"><Metric value="38" label="orders today" delta="+12%" tone="tangerine" /><Metric value="₹6.8k" label="revenue today" delta="+18%" tone="aqua" /><Metric value="4.8★" label="outlet rating" delta="steady" tone="lavender" /></div><div className="ops-layout"><section className="ops-panel"><div className="panel-heading"><div><span className="eyebrow">LIVE QUEUE</span><h2>Incoming orders</h2></div><span className="panel-count">04 active</span></div><div className="vendor-order-list"><VendorOrder id="#CRV-4824" item="Miso Crunch Bowl × 1" time="just now" status="New" tone="tangerine" /><VendorOrder id="#CRV-4823" item="Peri Peri Paneer Melt × 2" time="2 min ago" status="Cooking" tone="aqua" /><VendorOrder id="#CRV-4822" item="Loaded Campus Fries × 1" time="6 min ago" status="Ready" tone="chartreuse" /></div></section><section className="ops-panel menu-status-panel"><div className="panel-heading"><div><span className="eyebrow">QUICK MENU</span><h2>Availability</h2></div><button className="text-button" onClick={() => navigate('/vendor/menu')}>full menu ↗</button></div>{foods.slice(0, 4).map((food) => <div className="availability-row" key={food.id}><span className="availability-dot" /><div><strong>{food.name}</strong><small>{food.prep} · {money(food.price)}</small></div><button className="toggle-on">ON</button></div>)}</section></div></div>
}

function Metric({ value, label, delta, tone }: { value: string; label: string; delta: string; tone: Tone }) {
  return <div className={`metric-card tone-${tone}`}><span className="metric-value">{value}</span><span className="metric-label">{label}</span><span className="metric-delta">{delta} ↗</span></div>
}

function VendorOrder({ id, item, time, status, tone }: { id: string; item: string; time: string; status: string; tone: Tone }) {
  return <div className="vendor-order"><div className={`vendor-order-icon tone-${tone}`}>↗</div><div className="vendor-order-copy"><strong>{id}</strong><span>{item}</span><small>{time}</small></div><span className={`order-state state-${status.toLowerCase()}`}>{status}</span><button className="dots-button">•••</button></div>
}

function AdminPage({ outlets, navigate, onRole }: { outlets: Outlet[]; navigate: (path: string) => void; onRole: (role: Role) => void }) {
  return <div className="page page-ops page-admin"><div className="ops-heading"><div><span className="eyebrow orange-ink">ADMIN HQ · SRMIST CAMPUS</span><h1>Campus at<br /><em>a glance.</em></h1></div><div className="ops-status"><span className="pulse-dot" /> all systems playful <button onClick={() => onRole('student')}>exit demo ↗</button></div></div><div className="metric-row"><Metric value="1,284" label="active students" delta="+8.2%" tone="tangerine" /><Metric value="18" label="live outlets" delta="all online" tone="aqua" /><Metric value="96%" label="order happiness" delta="+4.1%" tone="chartreuse" /></div><div className="admin-grid"><section className="ops-panel campus-health"><div className="panel-heading"><div><span className="eyebrow">CAMPUS PULSE</span><h2>Busy, in a good way.</h2></div><span className="panel-count">LIVE</span></div><div className="bar-chart">{[34, 52, 44, 76, 62, 88, 70, 96, 68, 78, 58, 84].map((height, index) => <div key={index} className="bar-column"><span style={{ height: `${height}%` }} /><small>{index + 9}</small></div>)}</div><div className="chart-legend"><span><i className="legend-orange" /> orders</span><span><i className="legend-dark" /> happy students</span></div></section><section className="ops-panel outlet-health"><div className="panel-heading"><div><span className="eyebrow">OUTLET HEALTH</span><h2>Everyone’s open.</h2></div><button className="text-button">manage ↗</button></div>{outlets.slice(0, 4).map((outlet) => <div className="outlet-health-row" key={outlet.id}><span className="health-emoji">{outlet.emoji}</span><div><strong>{outlet.name}</strong><small>{outlet.orders} · {outlet.eta}</small></div><span className="health-open">{outlet.open ? 'OPEN' : 'CLOSED'}</span></div>)}</section></div><section className="admin-note"><span className="note-star">✷</span><div><span className="eyebrow">ADMIN NOTE</span><h2>Peak lunch is 12:30–1:15.</h2><p>Maybe nudge pickup slots earlier? Or just keep the snacks coming. Your call.</p></div><button className="button button-dark">view insights <span>↗</span></button></section></div>
}

function CartRail({ cart, total, count, navigate, setItemQuantity, order }: { cart: CartItem[]; total: number; count: number; navigate: (path: string) => void; setItemQuantity: (id: string, quantity: number) => void; order: typeof demoOrder }) {
  return <aside className="cart-rail"><div className="rail-top"><span className="eyebrow">QUICK LOOK</span><button className="icon-button">•••</button></div>{count > 0 ? <div className="rail-cart"><div className="rail-heading"><h2>Your bag <span>{count}</span></h2><button className="text-button" onClick={() => navigate('/cart')}>edit ↗</button></div>{cart.slice(0, 3).map((item) => <div className="rail-item" key={item.id}><span className={`rail-item-emoji tone-${item.tone}`}>{item.emoji}</span><div><strong>{item.name}</strong><small>{item.quantity} × {money(item.price)}</small></div><button onClick={() => setItemQuantity(item.id, item.quantity - 1)}>×</button></div>)}{count > 3 && <small className="more-items">+ {count - 3} more things</small>}<div className="rail-total"><span>Subtotal</span><strong>{money(total)}</strong></div><button className="button button-primary full-button" onClick={() => navigate('/cart')}>Review bag <span>↗</span></button></div> : <div className="rail-empty"><div className="empty-doodle">✦</div><h2>Bag’s empty,<br /><em>mind’s busy?</em></h2><p>We know a few things that could help with that.</p><button className="text-button" onClick={() => navigate('/')}>find a bite <span>↗</span></button></div>}<div className="rail-divider" /><div className="rail-order"><div className="rail-heading"><div><span className="eyebrow">IN THE WILD</span><h2>Current order</h2></div><span className="mini-live" /></div><p>{order.outlet} · {order.itemLabel.split(' · ')[0]}</p><div className="mini-progress"><span style={{ width: order.status === 'Queued' ? '30%' : '65%' }} /></div><div className="mini-progress-meta"><span>{order.status}</span><strong>{order.pickup}</strong></div><button className="text-button" onClick={() => navigate(`/orders/${order.id}`)}>track it <span>↗</span></button></div><div className="rail-sticker">NO QUEUE<br /><strong>ENERGY</strong></div></aside>
}

function EmptyState({ title, copy, action, onAction }: { title: string; copy: string; action?: string; onAction?: () => void }) {
  return <div className="empty-state"><div className="empty-shape">✦</div><h2>{title}</h2><p>{copy}</p>{action && <button className="button button-primary" onClick={onAction}>{action} <span>↗</span></button>}</div>
}

function isActivePath(current: string, target: string) {
  if (target === '/') return current === '/'
  return current.startsWith(target)
}

function prettyPage(path: string) {
  if (path === '/') return 'Discover'
  if (path.startsWith('/menu')) return 'Menu'
  if (path.startsWith('/food')) return 'Food detail'
  if (path.startsWith('/cart')) return 'Your bag'
  if (path.startsWith('/checkout')) return 'Checkout'
  if (path.startsWith('/orders/')) return 'Live order'
  if (path.startsWith('/orders')) return 'My orders'
  if (path.startsWith('/favorites')) return 'Saved bites'
  if (path.startsWith('/profile')) return 'Profile'
  return 'Discover'
}

export default App
