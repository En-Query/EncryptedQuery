desc "Show last exception"
task :last_error do
  on roles(:all) do |server|
    server.roles.each do |role|
      if role == :responder
        cmd = "#{current_path}/responder"
      elsif role == :querier
        cmd = "#{current_path}/querier"
      end
      cmd += "/bin/client log:exception-display"
      execute "#{cmd}"
    end
  end
end
