

Summary:
-------
Given a file containing users with playlists and playlists with songs, allow for various modifications, then return an updated file. 
The code is packaged as a jar file, taking two inputs, and generating a new file named the same as the input plus a timestamp.
A set of two files is provided for testing, under test/resources

How To Use:
-----------
From the directory containing the java classes:
- compile using the following command: javac -classpath ".:lib/jackson-core-2.9.7.jar:lib/jackson-databind-2.9.7.jar:lib/jackson-annotations-2.9.7.jar"  *.java
- excute the resulting jar using the provided script run.sh, which defaults to using the test sample inputs. The output will be in a file testChange_<timestamp>.txt.


Solution Details:
-----------------
The simplest solution conceptually is to represent both input and changes roughly as json trees, and then merge nodes. However, reading json into memory is not scalable. The implemented solution is more scalable, certain assumptions having been made as follows:
- users, playlists and songs are represented only by ids. The metadata for each is stored elsewhere.
- only a subset of the possible CRUD operations are implemented, but the code can be easily extended to other operations.
- the users, playlists and songs in the input file are assumed to be sorted by id, and that the order of attributes is the same (userId, playlists)

The input file is not JSON, but a format optimized for ingestion and sorting; it looks like user=<id>/playlist=<id>/songId=<ids>/action=<predefined action>. For very large input files, they can be sorted with a simple Unix sort, in order to group actions for a specific user together, then broken up into reasonable chunks by user boundaries for processing. 

First, the changes file is ingested, changes are parsed and hashed by user id.

Then, the input file is streamed; once an entire user is ingested, the associated changes are applied; the resulting object is serialized to an output stream.

The Jackson library is used to aid with serializing and deserializing.


Testing:
-------
There is only one test method, working with the provided test inputs. This part would have to be extended for production code.


