In order to use dedicated PubSub tables, you need to:
1. import mysql-schema.sql to Tigase MySQL DB
2. set Tigase configuration:
  pubsub/pubsub-repo-class = tigase.pubsub.repository.PubSubDAOJDBC
  pubsub/pubsub-repo-url = "same ad your user user-db-uri"
