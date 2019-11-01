desc 'Start karaf if not running'
task :start_if_not_running, :host, :dir do |t, args|
  dir = args[:dir]
  host = args[:host]
  on host do
    within "#{dir}" do
      current_status = capture("./status", raise_on_non_zero_exit: false)
      puts "Current status is: " + current_status
      if !current_status.start_with?("Running")
        execute "./start"
      else
        puts "Karaf was already running on #{host}:#{dir}"
      end
    end
  end
  t.reenable
end