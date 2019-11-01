desc "Stop Karaf"
task :stop do
  on roles(:all) do |server|
    #    puts "Server #{server} has roles #{server.roles}"
    #    expectedStatus = /(Running|^\s*$)/
    server.roles.each do |role|

      #      puts "Role: #{role} class #{role.class} objId = #{role.object_id} == #{:responder.object_id} "

      if role == :responder
        path = "#{current_path}/responder/bin"
      elsif role == :querier
        path = "#{current_path}/querier/bin"
      end

      #      puts "Server #{server} with role '#{role}' and path #{path}"

      #    if server.roles.include?(:responder)
      #      path = "#{current_path}/responder/bin"
      #    else if server.roles.include?(:querier)
      #      path = "#{current_path}/querier/bin"
      #    end

      if test "[ -d #{path} ]"
        current_status = capture("#{path}/status", raise_on_non_zero_exit: false)
        puts "Current status is: " + current_status
        if current_status.match?(/^Running/)
          execute "#{path}/stop"

          expected = /^Not Running/
          puts "Waiting for Karaf to stop on #{host}:#{path}"

          i = 0
          current_status = capture("#{path}/status 2>&1", raise_on_non_zero_exit: false)
          #            puts "Current status on #{host}:#{path} is '#{current_status}'"
          while i < 80 && !current_status.match?(expected)
            #              puts "Current status on #{host}:#{path} is '#{current_status}'"
            sleep 2
            i = i + 1;
            current_status = capture("#{path}/status 2>&1", raise_on_non_zero_exit: false)
          end

          if !current_status.match?(expected)
            raise "Karaf did not stop on #{host}:#{path} on time."
          end
          #            puts "Karaf status at #{host}:#{path} reached '#{expected}' successfully."
        else
          puts "Karaf was not running on #{host}:#{path}"
        end
      end
    end
  end
end
