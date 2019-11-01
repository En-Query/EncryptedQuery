desc 'Check health of all servers'
task :wait_until_healthy do
  roles(:all).each do |server|
    server.roles.each do |role|
      run_locally do
        api = fetch(:api_endpoints)["#{role}"]
        scheme = api["scheme"]
        port = api["port"]
        path = api["path"]
        url = "#{scheme}://#{server.hostname}:#{port}#{path}/health/status"
        wget = "wget --quiet --output-document=- #{url}  2>&1"
#        puts wget
        
        i = 0
        status = capture wget, raise_on_non_zero_exit: false
        while i < 30 && status != "up"
          puts "Waiting for #{role} @ #{server} to be healthy."
          sleep 3
          i = i + 1;
          status = capture wget, raise_on_non_zero_exit: false
        end

        if status == "up"
          puts "#{role} @ #{server} -> [OK]"
        else
          puts "#{role} @ #{server} -> [ERROR] (#{status})"
        end
      end
    end
  end
end
