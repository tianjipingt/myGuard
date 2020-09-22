@echo off

set guard-opts="-Dguard.kafka_home=F:\data197\kafka_2.13-2.5.0"

set guard-opts=%guard-opts% "-Dguard.kafka_logs_dir=F:\data197\kafka-logs"

set guard-opts=%guard-opts% "-Dguard.server_path=config/server.properties"

rem set guard-opts=%guard-opts% "-Dguard.cmd=.\kafka-my-start.bat"

java %guard-opts% KafkaGuard