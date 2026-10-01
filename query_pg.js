const { Client } = require('pg');

async function run() {
  // We do not have the password. So this will fail.
  const client = new Client({
    connectionString: 'postgresql://postgres.btdmhveaqssuuhyoyanz@aws-0-ap-northeast-1.pooler.supabase.com:5432/postgres'
  });
  try {
    await client.connect();
    console.log("Connected");
  } catch (e) {
    console.error(e.message);
  } finally {
    await client.end();
  }
}
run();
