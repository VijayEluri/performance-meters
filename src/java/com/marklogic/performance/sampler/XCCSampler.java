/*
 * Copyright (c)2005-2007 Mark Logic Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * The use of the Apache License does not indicate that this project is
 * affiliated with the Apache Software Foundation.
 */
package com.marklogic.performance.sampler;

import java.io.IOException;
import java.io.Reader;

import com.marklogic.performance.Configuration;
import com.marklogic.performance.Result;
import com.marklogic.performance.TestInterface;
import com.marklogic.performance.TestIterator;
import com.marklogic.xcc.ContentSource;
import com.marklogic.xcc.ContentSourceFactory;
import com.marklogic.xcc.Request;
import com.marklogic.xcc.RequestOptions;
import com.marklogic.xcc.ResultItem;
import com.marklogic.xcc.ResultSequence;
import com.marklogic.xcc.Session;
import com.marklogic.xcc.exceptions.XccException;
import com.marklogic.xcc.types.XdmVariable;

public class XCCSampler extends Sampler {

    // use char instead of superclass byte
    char[] readBuffer = new char[readsize];

    /**
     * @param ti
     * @param cfg
     */
    public XCCSampler(TestIterator ti, Configuration cfg) {
        super(ti, cfg);
    }

    public Result sample(TestInterface test) throws IOException {
        // time to make sure we have a connection:
        // do this per sample, in case Java's thread management isn't fair
        // new connection every time, to distribute load more evenly
        ContentSource cs = ContentSourceFactory.newContentSource(host,
                port, user, password);

        Result testResult = new Result(test.getName(), test
                .getCommentExpectedResult());
        testResult.setStart();
        StringBuffer resultsBuffer = new StringBuffer();

        // do some work
        String query = test.getQuery();

        try {
            Session sess = cs.newSession();
            // use uncached results, in case recordResults=false
            // and the actual results are huge.
            RequestOptions requestOptions = new RequestOptions();
            requestOptions.setCacheResult(false);
            Request req = sess.newAdhocQuery(query);
            req.setOptions(requestOptions);
            setVariables(req, test);
            ResultSequence rs = sess.submitRequest(req);
            testResult.incrementBytesSent(query.length());

            // handle results
            ResultItem item = null;
            int actual;
            while (rs.hasNext()) {
                item = rs.next();
                Reader buf = item.asReader();
                do {
                    actual = buf.read(readBuffer);
                    if (actual > 0) {
                        testResult.incrementBytesReceived(actual);
                        if (!reportTime || recordResults) {
                            resultsBuffer.append(readBuffer, 0, actual);
                        }
                    }
                } while (actual > 0);
            }
            rs.close();
            sess.close();

            // add the textual result to the results object,
            // if the configuration demands it.
            if (!reportTime || recordResults) {
                String resultsString = resultsBuffer.toString().trim();
                testResult.setQueryResult(resultsString);
                if (checkResults
                        && !test.getCommentExpectedResult().equals(
                                resultsString)) {
                    testResult.setError(true);
                }
            }
        } catch (XccException e) {
            String name = test.getName();
            System.err.println("Error running query: "
                    + (null != name ? name : query));
            e.printStackTrace();

            testResult.setError(true);
            String errorMessage = e.getMessage();
            if (errorMessage == null) {
                errorMessage = "NULL";
            }
            if (!reportTime || recordResults) {
                testResult.setQueryResult(errorMessage);
            }
        }
        testResult.setEnd();
        return testResult;
    }

    /**
     * @param req
     * @param test
     */
    private void setVariables(Request req, TestInterface test) {
        if (!test.hasVariables()) {
            return;
        }

        XdmVariable[] variables = test.getVariables();
        for (int i = 0; i < variables.length; i++) {
            if (null != variables[i]) {
                req.setVariable(variables[i]);
            }
        }
    }

}