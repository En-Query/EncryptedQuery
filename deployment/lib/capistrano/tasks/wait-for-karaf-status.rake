desc 'Wait for karaf status to be equal to a specific string value'
task :wait_until_status, :host, :dir, :expected_status do |t, args|

  server = args[:host]
  dir = args[:dir];
  expected = args[:expected_status];

  puts "Waiting for status on #{server}"

  on server do
    puts "Waiting for status on #{host}:#{dir} to be '#{expected}'"
    i = 0
    current_status = capture("#{dir}/status 2>&1", raise_on_non_zero_exit: false)
    puts "Current status on #{host}:#{dir} is '#{current_status}'"

    while i < 300 && !current_status.match?(expected)
      puts "Current status on #{host}:#{dir} is '#{current_status}'"
      sleep 1
      i = i + 1;
      current_status = capture("#{dir}/status 2>&1", raise_on_non_zero_exit: false)
    end

    if !current_status.match?(expected)
      raise "Karaf status at #{host}:#{dir} not " + current_status + " after 5 minutes."
    end

    puts "Karaf status at #{host}:#{dir} reached '#{expected}' successfully."
  end
  t.reenable
end