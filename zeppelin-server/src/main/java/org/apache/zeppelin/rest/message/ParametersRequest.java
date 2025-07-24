/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.zeppelin.rest.message;

import java.util.Map;

/**
 * ParametersRequest rest api request message.
 */
public class ParametersRequest {

  private final Map<String, Object> params;

  private final String runBy;

  private final Map<String, String> specificInterpreterGroupName;



  public ParametersRequest(Map<String, Object> params, String runBy, Map<String, String> newInterpreterGroupName) {
    this.params = params;
    this.runBy = runBy;
    this.specificInterpreterGroupName = newInterpreterGroupName;
  }

  public Map<String, Object> getParams() {
    return params;
  }
  

  public String getRunBy() {
    return runBy;
  }


  public Map<String, String> getSpecificInterpreterGroupName() {
    return specificInterpreterGroupName;
  }
}
