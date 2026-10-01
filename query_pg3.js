const { Client } = require('pg');

async function run() {
  const client = new Client({
    connectionString: 'postgresql://postgres.btdmhveaqssuuhyoyanz@aws-0-ap-northeast-1.pooler.supabase.com:5432/postgres'
  });
  try {
    await client.connect();
    console.log("Connected");
  } catch (e) {
    console.log("Error:");
    console.error(e);
  } finally {
    await client.end();
  }
}
run();
