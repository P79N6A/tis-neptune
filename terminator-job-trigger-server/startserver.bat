mvn test -Dtest=TestLaunchServer -DargLine="-Xrunjdwp:transport=dt_socket,address=9992,suspend=y,server=y -Dterminator_project=jst"