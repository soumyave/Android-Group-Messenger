# Android-Group-Messenger

1. Developed a failure tolerant Android group messenger and used a chord based distributed hash table for ring based routing, partitioning/repartitioning.
2. It also handles new node joins, sequential insert, query and delete operations. 
3. The application handles concurrent incoming messages and uses a simplified version of Amazon dynamo based distributed key-value storage system to provide both linearizability and availability.
