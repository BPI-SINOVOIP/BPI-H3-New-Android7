�汾��v1.2

����˵��:
	�̼�ǩ������

ʹ�÷�����
	./sign_fw.sh -i [old_fw.img] -k [key] -o [singed_fw.img]

����˵����
	���룺
		-i ����ǩ���Ĺ̼�
		-k ��RSA˽Ҫ��2048λ��pem��ʽ	
	�����
		-o ��ǩ�����Ĺ̼�

Ŀ¼�ṹ��
.
������ config                                  //�����ļ�
��   ������ cnf_base.cnf
��   ������ dragon_toc_a64_no_secureos.cfg
������ readme.txt                              //ʹ��˵��
������ sign_fw.sh                              //�̼�ǩ���ű�
������ tools                                   //����Ŀ¼
    ������ applypatch
    ������ dragonsecboot
    ������ FWmdf.so
    ������ fwpacker
    ������ req
    ������ shflags
    ������ x509
