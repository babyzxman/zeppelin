import requests
from py4j.java_gateway import java_import
from pyspark.sql.types import StructType, StructField, IntegerType, StringType, BooleanType, LongType, DateType, TimestampType
from pyspark.sql import Row
import time
from datetime import datetime, timedelta
import random
import string

import urllib3

# Suppress only the single InsecureRequestWarning from urllib3 needed
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

def hera_api_writeDeltaTable(prefixUrl, token, isVerify, notebookRefId, runBy, tableName, description
                             ,sink, sinkOptions, sc, storageName, timeField, partitions, deltaKeys, enableService):
    java_import(sc._jvm, "com.blendata.BlendataUtil")
    java_obj = sc._jvm.com.blendata.BlendataUtil

    valueMap = sc._jvm.java.util.HashMap()
    valueMap.put("tableName", tableName)
    valueMap.put("description", description)
    valueMap.put("sink", sink._jdf)
    valueMap.put("sinkOptions", sinkOptions)
    valueMap.put("spark", sc._jsc)
    valueMap.put("storageName", storageName)
    valueMap.put("timeField", timeField)
    valueMap.put("partitions", partitions)
    valueMap.put("deltaKeys", deltaKeys)
    valueMap.put("enableService", enableService)

    return java_obj.blendata_util.hera_api_writeTablePython(prefixUrl, token, isVerify, notebookRefId, runBy, valueMap, "delta")

def hera_api_writeTable(prefixUrl, token, isVerify, notebookRefId, runBy, tableName, description
                        ,sink, sinkOptions, sc, storageName, timeField, partitions, isEncryptParquet, enableService):
    java_import(sc._jvm, "com.blendata.BlendataUtil")
    java_obj = sc._jvm.com.blendata.BlendataUtil

    valueMap = sc._jvm.java.util.HashMap()
    valueMap.put("tableName", tableName)
    valueMap.put("description", description)
    valueMap.put("sink", sink._jdf)
    valueMap.put("sinkOptions", sinkOptions)
    valueMap.put("spark", sc._jsc)
    valueMap.put("storageName", storageName)
    valueMap.put("timeField", timeField)
    valueMap.put("partitions", partitions)
    valueMap.put("isEncryptParquet", isEncryptParquet)
    valueMap.put("enableService", enableService)

    return java_obj.blendata_util.hera_api_writeTablePython(prefixUrl, token, isVerify, notebookRefId, runBy, valueMap, "default")

def hera_api_runParallel(prefixUrl, token, isVerify, notebookRefId, runBy, timeSleep, timeout, concurrentParallel, language, notebookAddParameterRequest):
    moduleNotebookName = None
    if ":" in notebookRefId:
        moduleNameNotebookId = notebookRefId.split(":");
        moduleNotebookName = moduleNameNotebookId[0]
    runParallelUrl = prefixUrl + "/private/notebook/run/parallel"
    verify = False
    if isVerify == 1:
        verify = True
    body = {"timeSleep" : timeSleep,
            "moduleNotebookName" : moduleNotebookName,
            "timeout" : timeout,
            "concurrentParallel" : concurrentParallel,
            "language" : language,
            "notebookAddParameterRequest" : notebookAddParameterRequest,
            "runBy" : runBy}
    headers = {
        "Authorization": token
    }
    response = requests.post(runParallelUrl, json = body, headers=headers, verify=verify)
    if response.status_code == 200:
        return response.json()
    else:
        if response.json() is not None:
            raise ValueError(f"Run parallel fail case by: {response.json().get('message')}")
        raise ValueError("Run parallel fail")

def hera_api_runParallelLog(prefixUrl, token, isVerify, notebookRefId, runBy, timeSleep, timeout, concurrentParallel, language, notebookAddParameterRequest, description, runId=None):
    moduleNotebookName = None
    parentNotebookId = None
    retry = 0
    if ":" in notebookRefId:
        moduleNameNotebookId = notebookRefId.split(":")
        moduleNotebookName = moduleNameNotebookId[0]
        for item in notebookAddParameterRequest:
            item["parentNotebookId"] = moduleNameNotebookId[2]
            parentNotebookId = moduleNameNotebookId[2]
    if runId == None:
        random_str = generate_random_string(12)
        runId = "noRunId_" + random_str
        print("set default runId to " + runId)
    result = {}
    runParallelLogUrl = prefixUrl + "/private/notebook/start/session/parallel"
    verify = False
    if isVerify == 1:
        verify = True
    body = {"timeSleep" : timeSleep,
            "runningId" : runId,
            "moduleNotebookName" : moduleNotebookName,
            "timeout" : timeout,
            "concurrentParallel" : concurrentParallel,
            "language" : language,
            "notebookAddParameterRequest" : notebookAddParameterRequest,
            "description" : description,
            "runBy" : runBy}
    headers = {
        "Authorization": token
    }
    response = requests.post(runParallelLogUrl, json = body, headers=headers, verify=verify)
    if response.status_code != 200:
        if response.json() is not None:
            raise ValueError(f"Start parallel fail case by: {response.json().get('message')}")
        raise ValueError("Start parallel fail")
    now = datetime.now()
    end_time = now + timedelta(seconds=timeout)
    if (len(response.json()["parallelResult"]) > 0):
        for key, value in response.json()["parallelResult"]:
            value.pop("notebookIdRef")
            print(f"{key}: {value}")
    notebookRefIds = response.json()["notebookRefIds"]
    while (now < end_time and len(notebookRefIds) > 0):
        time.sleep(timeSleep)
        checkParallelLogUrl = prefixUrl + "/private/notebook/session/parallel/check"
        checkBody = {
            "noteRefIds" : notebookRefIds,
            "runningId" : runId,
            "concurrentParallel" : concurrentParallel,
            "runBy" : runBy
        }
        checkResponse = requests.post(checkParallelLogUrl, json = checkBody, headers=headers, verify=verify)
        # print(checkResponse.json())
        try:
            for key, value in checkResponse.json().items():
                if(value["status"] != "RUNNING" and value["status"] != "READY"):
                    notebookRefIds.remove(value["notebookIdRef"])
                    value.pop("notebookIdRef")
                    if(description != True and "message" in value):
                        value.pop("message")
                    result[key] = value
                    print(f"{key}: {value}")
        except Exception as e:
            retry += 1
            print(f"Error processing response: {e}")
            time.sleep(timeSleep)
        now = datetime.now()
        if retry >= 30:
            print("Maximum number of errors reached.")
            break
    if len(notebookRefIds) != 0:
        print(f"stop remaining parallel runningId: {runId}")
        stopParallelLogUrl = prefixUrl + "/private/notebook/session/parallel/stop"
        stopBody = {
            "runningId" : runId,
            "noteParentRefId" : parentNotebookId
        }
        stopResponse = requests.post(stopParallelLogUrl, json = stopBody, headers=headers, verify=verify)
    time.sleep(timeSleep)
    checkParallelLogUrl = prefixUrl + "/private/notebook/session/parallel/check"
    checkBody = {
        "noteRefIds" : notebookRefIds,
        "runningId" : runId,
        "concurrentParallel" : concurrentParallel
    }
    checkResponse = requests.post(checkParallelLogUrl, json = checkBody, headers=headers, verify=verify)
    if checkResponse.status_code == 200:
        for key, value in checkResponse.json().items():
            value.pop("notebookIdRef")
            result[key] = value
            print(f"{key}: {value}")
    return result



def encryptUser(url, username, password):
    encryptUserUrl = url + "/api/bde/encrypt/user"
    verify = False
    body = {
        "username": username,
        "password": password
    }
    response = requests.post(encryptUserUrl, json = body,verify=verify)
    return response.json().get('body')

def hera_api_dropTable(prefixUrl, token, isVerify, notebookRefId, runBy, sc, tableName):
    java_import(sc._jvm, "com.blendata.BlendataUtil")
    java_obj = sc._jvm.com.blendata.BlendataUtil

    return java_obj.blendata_util.hera_api_dropTable(prefixUrl, token, isVerify, notebookRefId, runBy, tableName)
def getUser(user):
    return user

def getModuleNotebookName(moduleNotebookName):
    return moduleNotebookName


def hera_api_runParallelLogOld(prefixUrl, token, isVerify, notebookRefId, runBy, timeSleep, timeout, concurrentParallel, language, notebookAddParameterRequest, description):
    moduleNotebookName = None
    if ":" in notebookRefId:
        moduleNameNotebookId = notebookRefId.split(":");
        moduleNotebookName = moduleNameNotebookId[0]
    runParallelLogUrl = prefixUrl + "/private/notebook/run/session/parallel"
    verify = False
    if isVerify == 1:
        verify = True
    body = {"timeSleep" : timeSleep,
            "moduleNotebookName" : moduleNotebookName,
            "timeout" : timeout,
            "concurrentParallel" : concurrentParallel,
            "language" : language,
            "notebookAddParameterRequest" : notebookAddParameterRequest,
            "description" : description,
            "runBy" : runBy}
    headers = {
        "Authorization": token
    }
    response = requests.post(runParallelLogUrl, json = body, headers=headers, verify=verify)
    if response.status_code == 200:
        return response.json()
    else:
        if response.json() is not None:
            raise ValueError(f"Run parallel fail case by: {response.json().get('message')}")
        raise ValueError("Run parallel fail")

def encryptUser(url, username, password):
    encryptUserUrl = url + "/api/bde/encrypt/user"
    verify = False
    body = {
        "username": username,
        "password": password
    }
    response = requests.post(encryptUserUrl, json = body,verify=verify)
    return response.json().get('body')

def hera_api_dropTable(prefixUrl, token, isVerify, notebookRefId, runBy, sc, tableName):
    java_import(sc._jvm, "com.blendata.BlendataUtil")
    java_obj = sc._jvm.com.blendata.BlendataUtil

    return java_obj.blendata_util.hera_api_dropTable(prefixUrl, token, isVerify, notebookRefId, runBy, tableName)
def getUser(user):
    return user

def getModuleNotebookName(moduleNotebookName):
    return moduleNotebookName

def importNoteName(url, z, noteName):
    importNoteUrl = url + "/api/notebook/name/" + noteName;
    verify = False
    response = requests.get(importNoteUrl, verify=verify)
    if response.status_code == 200:
        return z.importNotebook(response.json().get('body'))
    else:
        if response.json() is not None:
            raise ValueError(f"Import Notebook fail case by: {response.json().get('message')}")
        raise ValueError("Import Notebook fail")

def generate_random_string(length=10):
    characters = string.ascii_letters + string.digits  # a-zA-Z0-9
    return ''.join(random.choice(characters) for _ in range(length))