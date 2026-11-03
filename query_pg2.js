throw new Error("BLOCKED: this script targets PRODUCTION (btdmhveaqssuuhyoyanz). Remove this guard only with explicit approval.");
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
    console.error(e.message);
  } finally {
    await client.end();
  }
}
run();
