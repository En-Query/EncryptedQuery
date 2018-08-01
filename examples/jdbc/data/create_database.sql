drop database if exists enquery;
create database enquery character set utf8 collate utf8_bin;
create user 'enquery'@'%' identified by 'enquery';
grant all privileges on enquery.* to enquery;
