desc 'Prints current Karaf Status'
task :show_status do  
  on roles(:all) do
    if host.roles.include?(:responder)
      current_status =  capture "/opt/encrypted-query/current/responder/bin/status", raise_on_non_zero_exit: false
      puts "Responder status in '#{host}' is '#{current_status}'"
    end
    
    if host.roles.include?(:querier)
       current_status =  capture "/opt/encrypted-query/current/querier/bin/status", raise_on_non_zero_exit: false
       puts "Querier status in '#{host}' is '#{current_status}'"
    end
  end
end