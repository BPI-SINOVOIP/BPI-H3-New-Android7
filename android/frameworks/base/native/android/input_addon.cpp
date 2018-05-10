/*************************************************************************
    > File Name: input_addon.cpp
    > Author: chenjuncong
    > Mail: chenjuncong@allwinnertech.com
    > Created Time: 2017年11月09日 星期四 11时32分07秒
 ************************************************************************/
#include <stdio.h>
#include <unistd.h>
#include <string.h>

int ensureCallingProcesss(const char *processName){
    pid_t mpid = getpid();
    char mpath[30];
    sprintf(mpath,"/proc/%d/cmdline", mpid);
    FILE * fp = fopen(mpath, "r");
    if(fp == NULL){
        fclose(fp);
        return -1;
    }
    char buf[128];
    fgets(buf, sizeof(buf), fp);
    fclose(fp);
    if(!strncmp(processName, buf, strlen(processName))){
        return 0;
    }
    else{
        return -1;
    }
}
