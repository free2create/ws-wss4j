/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.swssf.wss.test;

import org.apache.ws.security.handler.WSHandlerConstants;
import org.swssf.wss.WSSec;
import org.swssf.wss.ext.*;
import org.swssf.wss.securityEvent.SecurityEvent;
import org.swssf.xmlsec.test.utils.StAX2DOM;
import org.swssf.xmlsec.test.utils.XmlReaderToWriter;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.*;

/**
 * @author $Author$
 * @version $Revision$ $Date$
 */
public class TimestampTest extends AbstractTestBase {

    @Test
    public void testTimestampDefaultConfigurationOutbound() throws Exception {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        {
            WSSSecurityProperties securityProperties = new WSSSecurityProperties();
            WSSConstants.Action[] actions = new WSSConstants.Action[]{WSSConstants.TIMESTAMP};
            securityProperties.setOutAction(actions);

            OutboundWSSec wsSecOut = WSSec.getOutboundWSSec(securityProperties);
            XMLStreamWriter xmlStreamWriter = wsSecOut.processOutMessage(baos, "UTF-8", new ArrayList<SecurityEvent>());
            XMLStreamReader xmlStreamReader = xmlInputFactory.createXMLStreamReader(this.getClass().getClassLoader().getResourceAsStream("testdata/plain-soap-1.1.xml"));
            XmlReaderToWriter.writeAll(xmlStreamReader, xmlStreamWriter);
            xmlStreamWriter.close();

            Document document = documentBuilderFactory.newDocumentBuilder().parse(new ByteArrayInputStream(baos.toByteArray()));
            NodeList nodeList = document.getElementsByTagNameNS(WSSConstants.TAG_wsu_Timestamp.getNamespaceURI(), WSSConstants.TAG_wsu_Timestamp.getLocalPart());
            Assert.assertEquals(nodeList.getLength(), 1);
            Assert.assertEquals(nodeList.item(0).getParentNode().getLocalName(), WSSConstants.TAG_wsse_Security.getLocalPart());

            Element created = (Element) ((Element) nodeList.item(0)).getElementsByTagNameNS(WSSConstants.TAG_wsu_Created.getNamespaceURI(), WSSConstants.TAG_wsu_Created.getLocalPart()).item(0);
            Element expires = (Element) ((Element) nodeList.item(0)).getElementsByTagNameNS(WSSConstants.TAG_wsu_Expires.getNamespaceURI(), WSSConstants.TAG_wsu_Expires.getLocalPart()).item(0);

            DatatypeFactory datatypeFactory = DatatypeFactory.newInstance();
            GregorianCalendar gregorianCalendarCreated = datatypeFactory.newXMLGregorianCalendar(created.getTextContent()).toGregorianCalendar();
            GregorianCalendar gregorianCalendarExpires = datatypeFactory.newXMLGregorianCalendar(expires.getTextContent()).toGregorianCalendar();

            Assert.assertTrue(gregorianCalendarCreated.before(gregorianCalendarExpires));
            GregorianCalendar now = new GregorianCalendar();
            Assert.assertTrue(now.after(gregorianCalendarCreated));
            Assert.assertTrue(now.before(gregorianCalendarExpires));

            gregorianCalendarCreated.add(Calendar.SECOND, 301);
            Assert.assertTrue(gregorianCalendarCreated.after(gregorianCalendarExpires));
        }

        //done timestamp; now test timestamp verification:
        {
            String action = WSHandlerConstants.TIMESTAMP;
            doInboundSecurityWithWSS4J(documentBuilderFactory.newDocumentBuilder().parse(new ByteArrayInputStream(baos.toByteArray())), action);
        }
    }

    @Test
    public void testTimestampDefaultConfigurationInbound() throws Exception {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        {
            InputStream sourceDocument = this.getClass().getClassLoader().getResourceAsStream("testdata/plain-soap-1.1.xml");
            String action = WSHandlerConstants.TIMESTAMP;
            Document securedDocument = doOutboundSecurityWithWSS4J(sourceDocument, action, new Properties());

            //some test that we can really sure we get what we want from WSS4J
            NodeList nodeList = securedDocument.getElementsByTagNameNS(WSSConstants.TAG_wsu_Timestamp.getNamespaceURI(), WSSConstants.TAG_wsu_Timestamp.getLocalPart());
            Assert.assertEquals(nodeList.item(0).getParentNode().getLocalName(), WSSConstants.TAG_wsse_Security.getLocalPart());

            javax.xml.transform.Transformer transformer = TRANSFORMER_FACTORY.newTransformer();
            transformer.transform(new DOMSource(securedDocument), new StreamResult(baos));
        }

        //done timestamp; now test timestamp-verification:
        {
            WSSSecurityProperties securityProperties = new WSSSecurityProperties();
            InboundWSSec wsSecIn = WSSec.getInboundWSSec(securityProperties);
            XMLStreamReader xmlStreamReader = wsSecIn.processInMessage(xmlInputFactory.createXMLStreamReader(new ByteArrayInputStream(baos.toByteArray())));

            Document document = StAX2DOM.readDoc(documentBuilderFactory.newDocumentBuilder(), xmlStreamReader);

            //header element must still be there
            NodeList nodeList = document.getElementsByTagNameNS(WSSConstants.TAG_wsu_Timestamp.getNamespaceURI(), WSSConstants.TAG_wsu_Timestamp.getLocalPart());
            Assert.assertEquals(nodeList.getLength(), 1);
            Assert.assertEquals(nodeList.item(0).getParentNode().getLocalName(), WSSConstants.TAG_wsse_Security.getLocalPart());
        }
    }

    @Test
    public void testTimestampTTLOutbound() throws Exception {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        {
            WSSSecurityProperties securityProperties = new WSSSecurityProperties();
            WSSConstants.Action[] actions = new WSSConstants.Action[]{WSSConstants.TIMESTAMP};
            securityProperties.setOutAction(actions);
            securityProperties.setTimestampTTL(3600);

            OutboundWSSec wsSecOut = WSSec.getOutboundWSSec(securityProperties);
            XMLStreamWriter xmlStreamWriter = wsSecOut.processOutMessage(baos, "UTF-8", new ArrayList<SecurityEvent>());
            XMLStreamReader xmlStreamReader = xmlInputFactory.createXMLStreamReader(this.getClass().getClassLoader().getResourceAsStream("testdata/plain-soap-1.1.xml"));
            XmlReaderToWriter.writeAll(xmlStreamReader, xmlStreamWriter);
            xmlStreamWriter.close();

            Document document = documentBuilderFactory.newDocumentBuilder().parse(new ByteArrayInputStream(baos.toByteArray()));
            NodeList nodeList = document.getElementsByTagNameNS(WSSConstants.TAG_wsu_Timestamp.getNamespaceURI(), WSSConstants.TAG_wsu_Timestamp.getLocalPart());
            Assert.assertEquals(nodeList.getLength(), 1);
            Assert.assertEquals(nodeList.item(0).getParentNode().getLocalName(), WSSConstants.TAG_wsse_Security.getLocalPart());

            Element created = (Element) ((Element) nodeList.item(0)).getElementsByTagNameNS(WSSConstants.TAG_wsu_Created.getNamespaceURI(), WSSConstants.TAG_wsu_Created.getLocalPart()).item(0);
            Element expires = (Element) ((Element) nodeList.item(0)).getElementsByTagNameNS(WSSConstants.TAG_wsu_Expires.getNamespaceURI(), WSSConstants.TAG_wsu_Expires.getLocalPart()).item(0);

            DatatypeFactory datatypeFactory = DatatypeFactory.newInstance();
            GregorianCalendar gregorianCalendarCreated = datatypeFactory.newXMLGregorianCalendar(created.getTextContent()).toGregorianCalendar();
            GregorianCalendar gregorianCalendarExpires = datatypeFactory.newXMLGregorianCalendar(expires.getTextContent()).toGregorianCalendar();

            Assert.assertTrue(gregorianCalendarCreated.before(gregorianCalendarExpires));
            GregorianCalendar now = new GregorianCalendar();
            Assert.assertTrue(now.after(gregorianCalendarCreated));
            Assert.assertTrue(now.before(gregorianCalendarExpires));

            gregorianCalendarCreated.add(Calendar.SECOND, 3601);
            Assert.assertTrue(gregorianCalendarCreated.after(gregorianCalendarExpires));
        }

        //done timestamp; now test timestamp verification:
        {
            String action = WSHandlerConstants.TIMESTAMP;
            doInboundSecurityWithWSS4J(documentBuilderFactory.newDocumentBuilder().parse(new ByteArrayInputStream(baos.toByteArray())), action);
        }
    }

    @Test
    public void testTimestampExpiredInbound() throws Exception {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        {
            InputStream sourceDocument = this.getClass().getClassLoader().getResourceAsStream("testdata/plain-soap-1.1.xml");
            String action = WSHandlerConstants.TIMESTAMP;
            Properties outboundProperties = new Properties();
            outboundProperties.setProperty(WSHandlerConstants.TTL_TIMESTAMP, "1");
            Document securedDocument = doOutboundSecurityWithWSS4J(sourceDocument, action, outboundProperties);

            //some test that we can really sure we get what we want from WSS4J
            NodeList nodeList = securedDocument.getElementsByTagNameNS(WSSConstants.TAG_wsu_Timestamp.getNamespaceURI(), WSSConstants.TAG_wsu_Timestamp.getLocalPart());
            Assert.assertEquals(nodeList.item(0).getParentNode().getLocalName(), WSSConstants.TAG_wsse_Security.getLocalPart());

            javax.xml.transform.Transformer transformer = TRANSFORMER_FACTORY.newTransformer();
            transformer.transform(new DOMSource(securedDocument), new StreamResult(baos));
        }

        Thread.sleep(1000);

        //done timestamp; now test timestamp-verification:
        {
            WSSSecurityProperties securityProperties = new WSSSecurityProperties();
            InboundWSSec wsSecIn = WSSec.getInboundWSSec(securityProperties);
            XMLStreamReader xmlStreamReader = wsSecIn.processInMessage(xmlInputFactory.createXMLStreamReader(new ByteArrayInputStream(baos.toByteArray())));

            try {
                StAX2DOM.readDoc(documentBuilderFactory.newDocumentBuilder(), xmlStreamReader);
                Assert.fail("Expected XMLStreamException");
            } catch (XMLStreamException e) {
                Assert.assertNotNull(e.getCause());
                Assert.assertTrue(e.getCause() instanceof WSSecurityException);
                Assert.assertEquals(e.getCause().getMessage(), "The message has expired (WSSecurityEngine: Invalid timestamp: The security semantics of the message have expired)");
            }
        }
    }

    @Test
    public void testTimestampExpiredEncryptedInbound() throws Exception {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        {
            InputStream sourceDocument = this.getClass().getClassLoader().getResourceAsStream("testdata/plain-soap-1.1.xml");
            String action = WSHandlerConstants.TIMESTAMP + " " + WSHandlerConstants.ENCRYPT;
            Properties outboundProperties = new Properties();
            outboundProperties.setProperty(WSHandlerConstants.TTL_TIMESTAMP, "1");
            outboundProperties.setProperty(WSHandlerConstants.ENCRYPTION_PARTS, "{Element}{http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd}Timestamp;");
            Document securedDocument = doOutboundSecurityWithWSS4J(sourceDocument, action, outboundProperties);

            //some test that we can really sure we get what we want from WSS4J
            NodeList nodeList = securedDocument.getElementsByTagNameNS(WSSConstants.TAG_xenc_EncryptedKey.getNamespaceURI(), WSSConstants.TAG_xenc_EncryptedKey.getLocalPart());
            Assert.assertEquals(nodeList.item(0).getParentNode().getLocalName(), WSSConstants.TAG_wsse_Security.getLocalPart());

            javax.xml.transform.Transformer transformer = TRANSFORMER_FACTORY.newTransformer();
            transformer.transform(new DOMSource(securedDocument), new StreamResult(baos));
        }

        Thread.sleep(1000);

        //done timestamp; now test timestamp-verification:
        {
            WSSSecurityProperties securityProperties = new WSSSecurityProperties();
            securityProperties.setCallbackHandler(new CallbackHandlerImpl());
            securityProperties.loadDecryptionKeystore(this.getClass().getClassLoader().getResource("receiver.jks"), "default".toCharArray());
            InboundWSSec wsSecIn = WSSec.getInboundWSSec(securityProperties);
            XMLStreamReader xmlStreamReader = wsSecIn.processInMessage(xmlInputFactory.createXMLStreamReader(new ByteArrayInputStream(baos.toByteArray())));

            try {
                StAX2DOM.readDoc(documentBuilderFactory.newDocumentBuilder(), xmlStreamReader);
                Assert.fail("Expected XMLStreamException");
            } catch (XMLStreamException e) {
                Assert.assertNotNull(e.getCause());
                Assert.assertTrue(e.getCause() instanceof WSSecurityException);
                Assert.assertEquals(e.getCause().getMessage(), "The message has expired (WSSecurityEngine: Invalid timestamp: The security semantics of the message have expired)");
            }
        }
    }

    @Test
    public void testTimestampInFutureInbound() throws Exception {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        {
            InputStream sourceDocument = this.getClass().getClassLoader().getResourceAsStream("testdata/plain-soap-1.1.xml");
            String action = WSHandlerConstants.TIMESTAMP;
            Properties outboundProperties = new Properties();
            outboundProperties.setProperty(WSHandlerConstants.TTL_TIMESTAMP, "1");
            Document securedDocument = doOutboundSecurityWithWSS4J(sourceDocument, action, outboundProperties);

            //some test that we can really sure we get what we want from WSS4J
            NodeList nodeList = securedDocument.getElementsByTagNameNS(WSSConstants.TAG_wsu_Timestamp.getNamespaceURI(), WSSConstants.TAG_wsu_Timestamp.getLocalPart());
            Assert.assertEquals(nodeList.item(0).getParentNode().getLocalName(), WSSConstants.TAG_wsse_Security.getLocalPart());

            Element created = (Element) ((Element) nodeList.item(0)).getElementsByTagNameNS(WSSConstants.TAG_wsu_Created.getNamespaceURI(), WSSConstants.TAG_wsu_Created.getLocalPart()).item(0);
            Element expires = (Element) ((Element) nodeList.item(0)).getElementsByTagNameNS(WSSConstants.TAG_wsu_Expires.getNamespaceURI(), WSSConstants.TAG_wsu_Expires.getLocalPart()).item(0);

            DatatypeFactory datatypeFactory = DatatypeFactory.newInstance();
            GregorianCalendar gregorianCalendarCreated = new GregorianCalendar();
            gregorianCalendarCreated.add(Calendar.HOUR, 2);
            XMLGregorianCalendar xmlGregorianCalendarCreated = datatypeFactory.newXMLGregorianCalendar(gregorianCalendarCreated);
            created.setTextContent(xmlGregorianCalendarCreated.toXMLFormat());

            GregorianCalendar gregorianCalendarExpires = new GregorianCalendar();
            gregorianCalendarExpires.add(Calendar.HOUR, 2);
            gregorianCalendarExpires.add(Calendar.SECOND, 300);
            XMLGregorianCalendar xmlGregorianCalendarExpires = datatypeFactory.newXMLGregorianCalendar(gregorianCalendarExpires);

            expires.setTextContent(xmlGregorianCalendarExpires.toXMLFormat());

            javax.xml.transform.Transformer transformer = TRANSFORMER_FACTORY.newTransformer();
            transformer.transform(new DOMSource(securedDocument), new StreamResult(baos));
        }

        //done timestamp; now test timestamp-verification:
        {
            WSSSecurityProperties securityProperties = new WSSSecurityProperties();
            InboundWSSec wsSecIn = WSSec.getInboundWSSec(securityProperties);
            XMLStreamReader xmlStreamReader = wsSecIn.processInMessage(xmlInputFactory.createXMLStreamReader(new ByteArrayInputStream(baos.toByteArray())));

            try {
                StAX2DOM.readDoc(documentBuilderFactory.newDocumentBuilder(), xmlStreamReader);
                Assert.fail("Expected XMLStreamException");
            } catch (XMLStreamException e) {
                Assert.assertNotNull(e.getCause());
                Assert.assertTrue(e.getCause() instanceof WSSecurityException);
                Assert.assertEquals(e.getCause().getMessage(), "The message has expired (WSSecurityEngine: Invalid timestamp: The security semantics of the message is invalid)");
            }
        }
    }

    @Test
    public void testTimestampStrictOffInbound() throws Exception {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        {
            InputStream sourceDocument = this.getClass().getClassLoader().getResourceAsStream("testdata/plain-soap-1.1.xml");
            String action = WSHandlerConstants.TIMESTAMP;
            Properties outboundProperties = new Properties();
            outboundProperties.setProperty(WSHandlerConstants.TTL_TIMESTAMP, "1");
            Document securedDocument = doOutboundSecurityWithWSS4J(sourceDocument, action, outboundProperties);

            //some test that we can really sure we get what we want from WSS4J
            NodeList nodeList = securedDocument.getElementsByTagNameNS(WSSConstants.TAG_wsu_Timestamp.getNamespaceURI(), WSSConstants.TAG_wsu_Timestamp.getLocalPart());
            Assert.assertEquals(nodeList.item(0).getParentNode().getLocalName(), WSSConstants.TAG_wsse_Security.getLocalPart());

            javax.xml.transform.Transformer transformer = TRANSFORMER_FACTORY.newTransformer();
            transformer.transform(new DOMSource(securedDocument), new StreamResult(baos));
        }

        Thread.sleep(1000);

        //done timestamp; now test timestamp-verification:
        {
            WSSSecurityProperties securityProperties = new WSSSecurityProperties();
            securityProperties.setStrictTimestampCheck(false);
            InboundWSSec wsSecIn = WSSec.getInboundWSSec(securityProperties);
            XMLStreamReader xmlStreamReader = wsSecIn.processInMessage(xmlInputFactory.createXMLStreamReader(new ByteArrayInputStream(baos.toByteArray())));

            StAX2DOM.readDoc(documentBuilderFactory.newDocumentBuilder(), xmlStreamReader);
        }
    }

    @Test
    public void testTimestampTTLInbound() throws Exception {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        {
            InputStream sourceDocument = this.getClass().getClassLoader().getResourceAsStream("testdata/plain-soap-1.1.xml");
            String action = WSHandlerConstants.TIMESTAMP;
            Properties outboundProperties = new Properties();
            outboundProperties.setProperty(WSHandlerConstants.TTL_TIMESTAMP, "300");
            Document securedDocument = doOutboundSecurityWithWSS4J(sourceDocument, action, outboundProperties);

            //some test that we can really sure we get what we want from WSS4J
            NodeList nodeList = securedDocument.getElementsByTagNameNS(WSSConstants.TAG_wsu_Timestamp.getNamespaceURI(), WSSConstants.TAG_wsu_Timestamp.getLocalPart());
            Assert.assertEquals(nodeList.item(0).getParentNode().getLocalName(), WSSConstants.TAG_wsse_Security.getLocalPart());

            javax.xml.transform.Transformer transformer = TRANSFORMER_FACTORY.newTransformer();
            transformer.transform(new DOMSource(securedDocument), new StreamResult(baos));
        }

        Thread.sleep(1000);

        //done timestamp; now test timestamp-verification:
        {
            WSSSecurityProperties securityProperties = new WSSSecurityProperties();
            securityProperties.setTimestampTTL(1);
            InboundWSSec wsSecIn = WSSec.getInboundWSSec(securityProperties);
            XMLStreamReader xmlStreamReader = wsSecIn.processInMessage(xmlInputFactory.createXMLStreamReader(new ByteArrayInputStream(baos.toByteArray())));

            try {
                StAX2DOM.readDoc(documentBuilderFactory.newDocumentBuilder(), xmlStreamReader);
                Assert.fail("Expected XMLStreamException");
            } catch (XMLStreamException e) {
                Assert.assertNotNull(e.getCause());
                Assert.assertTrue(e.getCause() instanceof WSSecurityException);
                Assert.assertEquals(e.getCause().getMessage(), "The message has expired (WSSecurityEngine: Invalid timestamp: The security semantics of the message have expired)");
            }
        }
    }

    @Test
    public void testTimestampNoCreatedDateInbound() throws Exception {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        {
            InputStream sourceDocument = this.getClass().getClassLoader().getResourceAsStream("testdata/plain-soap-1.1.xml");
            String action = WSHandlerConstants.TIMESTAMP;
            Document securedDocument = doOutboundSecurityWithWSS4J(sourceDocument, action, new Properties());

            //some test that we can really sure we get what we want from WSS4J
            NodeList nodeList = securedDocument.getElementsByTagNameNS(WSSConstants.TAG_wsu_Timestamp.getNamespaceURI(), WSSConstants.TAG_wsu_Timestamp.getLocalPart());
            Assert.assertEquals(nodeList.item(0).getParentNode().getLocalName(), WSSConstants.TAG_wsse_Security.getLocalPart());
            for (int i = 0; i < nodeList.item(0).getChildNodes().getLength(); i++) {
                Node node = nodeList.item(0).getChildNodes().item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE && node.getLocalName().equals("Created")) {
                    node.getParentNode().removeChild(node);
                }
            }

            javax.xml.transform.Transformer transformer = TRANSFORMER_FACTORY.newTransformer();
            transformer.transform(new DOMSource(securedDocument), new StreamResult(baos));
        }

        //done timestamp; now test timestamp-verification:
        {
            WSSSecurityProperties securityProperties = new WSSSecurityProperties();
            InboundWSSec wsSecIn = WSSec.getInboundWSSec(securityProperties);
            XMLStreamReader xmlStreamReader = wsSecIn.processInMessage(xmlInputFactory.createXMLStreamReader(new ByteArrayInputStream(baos.toByteArray())));

            try {
                StAX2DOM.readDoc(documentBuilderFactory.newDocumentBuilder(), xmlStreamReader);
                Assert.fail("Expected XMLStreamException");
            } catch (XMLStreamException e) {
                Throwable throwable = e.getCause();
                Assert.assertNotNull(throwable);
                Assert.assertTrue(throwable instanceof WSSecurityException);
                Assert.assertEquals(throwable.getMessage(), "An error was discovered processing the <wsse:Security> header (Created time is missing)");
            }
        }
    }

    @Test
    public void testTimestampNoExpiresDateInbound() throws Exception {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        {
            InputStream sourceDocument = this.getClass().getClassLoader().getResourceAsStream("testdata/plain-soap-1.1.xml");
            String action = WSHandlerConstants.TIMESTAMP;
            Document securedDocument = doOutboundSecurityWithWSS4J(sourceDocument, action, new Properties());

            //some test that we can really sure we get what we want from WSS4J
            NodeList nodeList = securedDocument.getElementsByTagNameNS(WSSConstants.TAG_wsu_Timestamp.getNamespaceURI(), WSSConstants.TAG_wsu_Timestamp.getLocalPart());
            Assert.assertEquals(nodeList.item(0).getParentNode().getLocalName(), WSSConstants.TAG_wsse_Security.getLocalPart());
            for (int i = 0; i < nodeList.item(0).getChildNodes().getLength(); i++) {
                Node node = nodeList.item(0).getChildNodes().item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE && node.getLocalName().equals("Expires")) {
                    node.getParentNode().removeChild(node);
                }
            }

            javax.xml.transform.Transformer transformer = TRANSFORMER_FACTORY.newTransformer();
            transformer.transform(new DOMSource(securedDocument), new StreamResult(baos));
        }

        //done timestamp; now test timestamp-verification:
        {
            WSSSecurityProperties securityProperties = new WSSSecurityProperties();
            InboundWSSec wsSecIn = WSSec.getInboundWSSec(securityProperties);
            XMLStreamReader xmlStreamReader = wsSecIn.processInMessage(xmlInputFactory.createXMLStreamReader(new ByteArrayInputStream(baos.toByteArray())));

            Document document = StAX2DOM.readDoc(documentBuilderFactory.newDocumentBuilder(), xmlStreamReader);

            //header element must still be there
            NodeList nodeList = document.getElementsByTagNameNS(WSSConstants.TAG_wsu_Timestamp.getNamespaceURI(), WSSConstants.TAG_wsu_Timestamp.getLocalPart());
            Assert.assertEquals(nodeList.getLength(), 1);
            Assert.assertEquals(nodeList.item(0).getParentNode().getLocalName(), WSSConstants.TAG_wsse_Security.getLocalPart());
        }
    }

    @Test
    public void testTimestampNoChildsInbound() throws Exception {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        {
            InputStream sourceDocument = this.getClass().getClassLoader().getResourceAsStream("testdata/plain-soap-1.1.xml");
            String action = WSHandlerConstants.TIMESTAMP;
            Document securedDocument = doOutboundSecurityWithWSS4J(sourceDocument, action, new Properties());

            //some test that we can really sure we get what we want from WSS4J
            NodeList nodeList = securedDocument.getElementsByTagNameNS(WSSConstants.TAG_wsu_Timestamp.getNamespaceURI(), WSSConstants.TAG_wsu_Timestamp.getLocalPart());
            Assert.assertEquals(nodeList.item(0).getParentNode().getLocalName(), WSSConstants.TAG_wsse_Security.getLocalPart());

            List<Node> nodesToRemove = new ArrayList<Node>();
            for (int i = 0; i < nodeList.item(0).getChildNodes().getLength(); i++) {
                Node node = nodeList.item(0).getChildNodes().item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE
                        && (node.getLocalName().equals("Created")) || node.getLocalName().equals("Expires")) {
                    nodesToRemove.add(node);
                }
            }
            for (int i = 0; i < nodesToRemove.size(); i++) {
                Node node = nodesToRemove.get(i);
                node.getParentNode().removeChild(node);
            }

            javax.xml.transform.Transformer transformer = TRANSFORMER_FACTORY.newTransformer();
            transformer.transform(new DOMSource(securedDocument), new StreamResult(baos));
        }

        //done timestamp; now test timestamp-verification:
        {
            WSSSecurityProperties securityProperties = new WSSSecurityProperties();
            InboundWSSec wsSecIn = WSSec.getInboundWSSec(securityProperties);
            XMLStreamReader xmlStreamReader = wsSecIn.processInMessage(xmlInputFactory.createXMLStreamReader(new ByteArrayInputStream(baos.toByteArray())));

            try {
                StAX2DOM.readDoc(documentBuilderFactory.newDocumentBuilder(), xmlStreamReader);
                Assert.fail("Expected XMLStreamException");
            } catch (XMLStreamException e) {
                Throwable throwable = e.getCause();
                Assert.assertNotNull(throwable);
                Assert.assertTrue(throwable instanceof WSSecurityException);
                Assert.assertEquals(throwable.getMessage(), "An error was discovered processing the <wsse:Security> header (Created time is missing)");
            }
        }
    }

    @Test
    public void testDoubleTimestamp() throws Exception {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        {
            InputStream sourceDocument = this.getClass().getClassLoader().getResourceAsStream("testdata/plain-soap-1.1.xml");
            String action = WSHandlerConstants.TIMESTAMP;
            Document securedDocument = doOutboundSecurityWithWSS4J(sourceDocument, action, new Properties());

            //some test that we can really sure we get what we want from WSS4J
            NodeList nodeList = securedDocument.getElementsByTagNameNS(WSSConstants.TAG_wsu_Timestamp.getNamespaceURI(), WSSConstants.TAG_wsu_Timestamp.getLocalPart());
            Assert.assertEquals(nodeList.item(0).getParentNode().getLocalName(), WSSConstants.TAG_wsse_Security.getLocalPart());

            Node parentNode = nodeList.item(0).getParentNode();
            Node node = nodeList.item(0).cloneNode(true);
            securedDocument.adoptNode(node);
            parentNode.appendChild(node);

            javax.xml.transform.Transformer transformer = TRANSFORMER_FACTORY.newTransformer();
            transformer.transform(new DOMSource(securedDocument), new StreamResult(baos));
        }

        //done timestamp; now test timestamp-verification:
        {
            WSSSecurityProperties securityProperties = new WSSSecurityProperties();
            InboundWSSec wsSecIn = WSSec.getInboundWSSec(securityProperties);
            XMLStreamReader xmlStreamReader = wsSecIn.processInMessage(xmlInputFactory.createXMLStreamReader(new ByteArrayInputStream(baos.toByteArray())));

            try {
                StAX2DOM.readDoc(documentBuilderFactory.newDocumentBuilder(), xmlStreamReader);
                Assert.fail("Expected XMLStreamException");
            } catch (XMLStreamException e) {
                Throwable throwable = e.getCause();
                Assert.assertNotNull(throwable);
                Assert.assertTrue(throwable instanceof WSSecurityException);
                Assert.assertEquals(throwable.getMessage(), "An error was discovered processing the <wsse:Security> header (WSSecurityEngine: Invalid timestamp: Message contains two or more timestamps)");
            }
        }
    }
}
