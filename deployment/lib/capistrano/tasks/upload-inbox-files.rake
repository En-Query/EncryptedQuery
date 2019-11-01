desc 'Uploads inbox files'
task :upload_inbox_files do
  inbox_files = {}
  invoke :collect_local_file_checksums, fetch(:inbox_dirs), inbox_files
  
  on roles(:responder) do
    output_path = File.join shared_path, "inbox"
    execute "mkdir", "-p #{output_path}" 

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