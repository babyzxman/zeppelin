package org.apache.zeppelin.service.bde.spark;


import org.apache.zeppelin.rest.bde.view.spark.SparkScopedResponse;
import org.apache.zeppelin.rest.bde.view.spark.SparkUsedResponse;
import org.eclipse.jetty.util.annotation.ManagedObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ManagedObject
public class SparkObject {

    public final Map<String, SparkUsedResponse> sparkInterpreterGroupNamePort = new ConcurrentHashMap<>();

    public final Map<String, SparkScopedResponse> sparkScopedMap = new ConcurrentHashMap<>();
}
