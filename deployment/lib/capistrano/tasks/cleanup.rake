desc "Cleanup after install."
task :remove_build_artifacts do
  on roles(:all) do |host|
    within "#{release_path}" do
      execute "rm", "-drf", 
          "deployment core filter flink hadoop health-status jpa json ",
          "null-encryption paillier-encryption parent seal-bfv-encryption standalone xml",
          "eclipse-encryptedquery-codestyle.xml eclipse-encryptedquery-template.xml"
    end
  end

  on roles(:responder) do |host|
    within "#{release_path}" do
      execute "rm", "responder.tar.gz"

      if !(host.roles.include? :querier)
        execute "rm", "-drf", "querier"
      end
    end
  end

  on roles(:querier) do |host|
    within "#{release_path}" do
      execute "rm", "querier.tar.gz"

      if !(host.roles.include? :responder)
        execute "rm", "-drf", "responder"
      end
    end
  end
end