export IRIS_PATH=/usr/irissys
#export IRIS_PATH=~/apps/iris2020
javac -cp $IRIS_PATH/dev/java/lib/JDK18/*:. model/*.java
javac -cp $IRIS_PATH/dev/java/lib/JDK18/*:. *.java
java -cp ~/apps/iris2020/dev/java/lib/JDK18/*:. XEPExample