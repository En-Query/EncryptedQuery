desc 'Check health of all servers'
task :health do
  roles(:all).each do |server|
    server.roles.each do |role| 
      run_locally do
#        puts "role: #{role}" 
        api = fetch(:api_endpoints)["#{role}"]
        scheme = api["scheme"]
        port = api["port"]
        path = api["path"]
        status = capture "wget --quiet --output-document=- #{scheme}://#{server.hostname}:#{port}#{path}/health/status  2>&1", raise_on_non_zero_exit: false
        if status == "up"
          puts "#{role} @ #{server} -> [OK]"
        else 
          puts "#{role} @ #{server} -> [ERROR] (#{status})"
        end
      end
    end
  end
end
