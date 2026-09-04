import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
fun check(auth: Auth) {
    auth.importAuthToken("access", "refresh")
}
