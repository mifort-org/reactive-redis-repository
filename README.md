# reactive-redis-repository
React Redis repository for Spring based application

There is no out of the box reactive based repositories for Redis to use with React code to use with Spring Web Flux. It's Reactor specific.
This is the small library to create high level entity repositories to store them in Redis.

It's only the first version which covers:
 - findById
 - deleteById
 - save
 
 Plans for future changes to support other base operations as well.