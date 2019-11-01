desc "Start Karaf"
task :start do
  on roles(:all) do |server|
    server.roles.each do |role|
      puts "#{ role }"
      if role == :responder
        path = "#{current_path}/responder/bin"
      elsif role == :querier
        path = "#{current_path}/querier/bin"
      end
      
      current_status = capture("#{path}/status", raise_on_non_zero_exit: false)
      if !current_status.start_with?("Running")
        execute "#{path}/start"
        
        expected = /^Running/
        puts "Waiting for Karaf to start on #{host}:#{path}"

        i = 0
        current_status = capture("#{path}/status 2>&1", raise_on_non_zero_exit: false)
        while i < 80 && !current_status.match?(expected)
          sleep 2
          i = i + 1;
          current_status = capture("#{path}/status 2>&1", raise_on_non_zero_exit: false)
        end

        if !current_status.match?(expected)
          raise "Karaf did not start on #{host}:#{path} on time."
        end
      else
        puts "Karaf was already running on #{host}:#{path}"
      end
    end
  end
end

