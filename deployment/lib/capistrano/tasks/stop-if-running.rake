desc 'Stop karaf if running'
task :stop_if_running, :host, :dir do |t, args|
  dir = args[:dir]
  host = args[:host]
#  on host do
    puts "Stopping Karaf on #{host}:#{dir}"
#    within "#{dir}" do
      current_status = capture("#{dir}/status", raise_on_non_zero_exit: false)
      puts "Current status is: " + current_status
      if current_status.match?(/Running/)
        execute "#{dir}/stop"
      else
        puts "Karaf was not running on #{host}:#{dir}"
      end
#    end
#  end
  t.reenable
end