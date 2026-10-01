const { Client } = require('pg');

async function check() {
  const c = new Client({
    connectionString: 'postgresql://postgres:dummy@btdmhveaqssuuhyoyanz.supabase.co:5432/postgres' // Just to see the network error type
  });
  try { await c.connect(); } catch(e) { console.log(e.message); }
}
check();
