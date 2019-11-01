desc "Tail Karaf"
task :tail do
  on roles(:all) do |server|
    tail = "tail -f"
    server.roles.each do |role|
      if role == :responder
        path = "#{current_path}/responder"
      elsif role == :querier
        path = "#{current_path}/querier"
      end
      tail << " " << path << "/data/log/karaf.log"
    end
    #puts "*** #{role}@#{server}"
    execute "#{tail}"
  end
end
