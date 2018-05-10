版本：v1.2

功能说明:
	固件签名工具

使用方法：
	./sign_fw.sh -i [old_fw.img] -k [key] -o [singed_fw.img]

参数说明：
	输入：
		-i ：待签名的固件
		-k ：RSA私要，2048位，pem格式	
	输出：
		-o ：签好名的固件

目录结构：
.
├── config                                  //配置文件
│   ├── cnf_base.cnf
│   └── dragon_toc_a64_no_secureos.cfg
├── readme.txt                              //使用说明
├── sign_fw.sh                              //固件签名脚本
└── tools                                   //工具目录
    ├── applypatch
    ├── dragonsecboot
    ├── FWmdf.so
    ├── fwpacker
    ├── req
    ├── shflags
    └── x509
