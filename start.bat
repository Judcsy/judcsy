@echo off
REM AI TestGen Pro 启动脚本
REM 请在此处填入您的API凭证

REM 火山引擎凭证
set ARK_API_KEY=4b0bc5fd-b42c-4bd9-aa90-add2c998c6e0
set VOLCANO_ENDPOINT_ID=ep-20251205021433-ds5vk

REM 飞书凭证
set FEISHU_APP_ID=cli_a9bb0c4654f8dcb0
set FEISHU_APP_SECRET=dYREeeWJsa0E7XmCUupiSeysbBDYS5pp

echo [启动] 环境变量已设置
echo [启动] 正在编译并运行...

mvn compile exec:java -q
