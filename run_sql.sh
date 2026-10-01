#!/bin/bash
curl -s -X POST 'https://btdmhveaqssuuhyoyanz.supabase.co/rest/v1/rpc/exec_sql' \
-H "apikey: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJ0ZG1odmVhcXNzdXVoeW95YW56Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODgwMjA5NzQsImV4cCI6MjEwMzU5Njk3NH0.QAtvfV5Z483R7hdYyBHr5jbAjB6b_SpiHYiXWgD0iAk" \
-H "Content-Type: application/json" \
-d "{\"query\": $(jq -Rs . < supabase/.temp/query_schema.sql)}"
