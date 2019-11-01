desc "Build with Maven"
task :maven do
  on roles(:all) do |server|
      skip = ""
      profile = " -P with-precompiled-native-libs "
      if "#{fetch :build_native_libraries}" == "true"
        profile = " -P native-libs "
        if server.properties.GPU_support != true
          skip = " -Dskip.gpu.native.libs=true "
        end
      end
      within "#{release_path}/parent" do
        puts "Building sources in server #{server}"
        execute "mvn", "clean install -DskipTests " + profile + skip, " > #{release_path}/build.log", "2>&1 "
      end 
  end
end

