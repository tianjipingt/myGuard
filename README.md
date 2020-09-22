windows 版本kafka 经常有文件锁定或文件被系统进程占用，无法删除的bug, 出现后kafka 会异常退出。 为了避免线上服务掉线，所以写了个守护进程。

使用: 在kafka安装目录新建 myGuard 文件夹，然后将编译的class丢进去，执行 kafka-guard-start.bat ,会生成 kafka-my-start_gen.bat ,然后将 kafka-my-start_gen.bat copy 到kafka 根目录，在执行kafka-guard-start.bat 启动kafka 。

copy gen.bat 这个一步，有个bug ，因工作目录是在kafka_home ,不是在myGuard , 以后有时间会在修改一下，但总体不影响使用 o(￣︶￣)o

启动参数 (已经写好在 kafka-guard-start.bat 中,按实际情况修改)

-Dguard.kafka_home=E:/kafka_2.13-2.5.0 kafka 安装目录

-Dguard.kafka_logs_dir=E:/kafka-logs kafka 数据日志目录

-Dguard.server_path=config/server.properties 配置文件 相对于kafka 安装目录

-Dguard.cmd=.\bin\windows\kafka-server-start.bat 这个参数如果你的机子没有出现不能启动的诡异情况，还是不要用了，可以直接忽视掉 ，意思就是通过guard 直接启动 kafka-server-start.bat ，然后做一些你想要的监视，可以自己写代码，说实在的window 的kafka 版本，真的不稳定，如果可能还是换linux 的。

-Dguard.delMode=delBug(默认值 删除出问题的目录) ,delAll(删除kafka_logs_dir目录下所有的文件)

kafka 出现文件占用了，凶残做法就是删掉全部数据日志目录，然后重启，所以 delAll 就是做这个事情的，所以不要写错目录了啊啊啊啊啊啊啊啊！ 如果只是想删除出问题的文件，就用delBug 参数，但是我发现，这种情况会导致consumer在以后会漏收数据，我怀疑是offset 出了问题。

guard 在启动情况下，执行kafka 自带的kafka-server-stop.bat 是没法停止的，guard 发现kafka 进程死掉了，会立即重启它，所以如果不想guard 监视了，回道myGuard 目录执行 kafka-guard-stop.bat 关闭监视，然后在kafka-server-stop.bat 就能正常停止了。
