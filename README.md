# Twitter Stream Server
A small REST server that aggregates a twitter stream

## To use
1. Run the command  `sbt -Daccess_token=<$accessToken> -Daccess_secret=<$accessSecret> -Dconsumer_key=<$consumerKey> -Dconsumer_secret=<consumerSecret> run`
2. Go to `localhost:8888` to see statistics about the tweets being ingested