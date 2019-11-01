# config valid for current version and patch releases of Capistrano
lock "~> 3.11.1"

set :application, "encrypted-query"
#set :repo_url, "https://github.com/En-Query/EncryptedQuery.git"
set :repo_url, "git@bitbucket.org:envpqc/encryptedquery.git"


# Default branch is :master
# ask :branch, `git rev-parse --abbrev-ref HEAD`.chomp

# Default deploy_to directory is /var/www/my_app_name
set :deploy_to, "/opt/encrypted-query"

# Default value for :format is :airbrussh.
# set :format, :airbrussh

# You can configure the Airbrussh format using :format_options.
# These are the defaults.
# set :format_options, command_output: true, log_file: "log/capistrano.log", color: :auto, truncate: :auto

# Default value for :pty is false
# set :pty, true

# Default value for :linked_files is []

# Default value for default_env is {}
# set :default_env, { path: "/opt/ruby/bin:$PATH" }

set :default_env, { path: "/usr/local/bin:$PATH" }

# Default value for local_user is ENV['USER']
# set :local_user, -> { `git config user.name`.chomp }

# Default value for keep_releases is 5
set :keep_releases, 2

# Uncomment the following to require manually verifying the host key before first deploy.
# set :ssh_options, verify_host_key: :secure

set :log_level, :error
# set :SSHKit.config.format, :dot

#  Cache repository   
set :deploy_via, :remote_cache

before "deploy:check",   :upload_inbox_files
before "deploy:check",   :upload_sample_data_files
after "deploy:starting", :stop
after "deploy:updated",  :maven
after "deploy:updated",  :install
after "deploy:updated",  :remove_build_artifacts
after "deploy:finished", :upload_config
after "deploy:finished", :start
after "deploy:finished", :wait_until_healthy


# The default Responder `inbox` directories 
# (can be overridden at the sate level) and with 
# environment variable INBOX_DIRS
if ENV["INBOX_DIRS"]
  set :inbox_dirs, ENV["INBOX_DIRS"].split(',')
else
  set :inbox_dirs, %w{ "config/inbox" }
end
