# EyeSpy

A small CSS reloader. Will watch files specified and tell the clients listening which file has been changed upon which the client will reload the approriate CSS file.

Can have actions taken when files in a directory change (like, editing .less/.sass files for example). The action can then run a command that compiles the newly edited files. The newly compiled file is then detected and the browser is refreshed.

## Usage

```bash
java -jar eyespy.jar file1 file2  
java -jar eyespy.jar --watch file-with-all-the-files-i-want-to-watch
java -jar eyespy.jar --settings settings-file
```

Example settings file
```clojure
{:notify ["/path/to/file/to/watch.css"]
 :actions [{:command "command to run after files in the watch dir has changed"
            :watch-dir "/path/to/a/directory/to/watch"}]}
```

Example bash script
```bash
#!/bin/sh

cd `dirname "$0"`
java -jar eyespy.jar --settings settings.clj
```

In Browser  
Load the eyespy.js script, then run the following:  
```javascript
  eyespy.init();
```

## Where?

http://emil0r.com/eyespy

## License

Copyright © 2012 Emil Bengtsson

Distributed under the Eclipse Public License, the same as Clojure.
