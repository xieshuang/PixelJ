Set shell = CreateObject("WScript.Shell")
shell.CurrentDirectory = left(WScript.ScriptFullName, instrRev(WScript.ScriptFullName, "\"))
shell.Run "cmd /c mvn javafx:run", 0, False
