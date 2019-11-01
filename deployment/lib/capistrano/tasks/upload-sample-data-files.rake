desc 'Uploads sample data files'
task :upload_sample_data_files do
  inbox_files = {}
  inbox_directories = ["config/sampledata"]
  invoke :collect_local_file_checksums, inbox_directories, inbox_files

  on roles(:responder) do
    output_path = File.join shared_path, "sampledata"
    execute "mkdir", "-p #{output_path}" 
#    execute "rm", "#{output_path}/*.*" , raise_on_non_zero_exit: false

    inbox_files.each do |file_name, local_checksum|
      remote_file_name = "#{output_path}/#{file_name}"
      lsum, lsize, lpath = local_checksum.split

      if test("[ -f #{remote_file_name} ]")
        remote_cksum = capture "cksum", remote_file_name
        rsum, rsize, rpath = remote_cksum.split

        if lsum != rsum
          upload! lpath, remote_file_name
        end
      else
        upload! lpath, remote_file_name
      end
    end
  end
end