{
  print "LOAD DATA LOCAL INFILE '" $1 "' INTO TABLE XETRA FIELDS TERMINATED BY ',' ENCLOSED BY '\"' LINES TERMINATED BY '\n' IGNORE 1 ROWS (ISIN, Mnemonic, SecurityDesc, SecurityType, Currency, SecurityID, @TradingDate, TradingTime, StartPrice, MaxPrice, MinPrice, EndPrice, TradedVolume, NumberOfTrades) SET TradingDate = STR_TO_DATE(@TradingDate, '%Y-%m-%d');";
}
