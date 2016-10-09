ECHO hello world

ECHO -e "Publishing maven snapshot...\n"

mvn clean deploy --settings="util/settings.xml" -DskipTests=true -Dinvoker.skip=true -Dmaven.javadoc.skip=true

ECHO -e "Published maven snapshot"
