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
package org.swssf.xmlsec.impl.processor.input;

import org.swssf.binding.excc14n.InclusiveNamespaces;
import org.swssf.binding.xmldsig.CanonicalizationMethodType;
import org.swssf.binding.xmldsig.KeyInfoType;
import org.swssf.binding.xmldsig.SignatureType;
import org.swssf.xmlsec.ext.*;
import org.swssf.xmlsec.impl.algorithms.SignatureAlgorithm;
import org.swssf.xmlsec.impl.algorithms.SignatureAlgorithmFactory;
import org.swssf.xmlsec.impl.securityToken.SecurityTokenFactory;
import org.swssf.xmlsec.impl.util.SignerOutputStream;

import javax.xml.bind.JAXBElement;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * @author $Author$
 * @version $Revision$ $Date$
 */
public abstract class AbstractSignatureInputHandler extends AbstractInputSecurityHeaderHandler {

    @Override
    public void handle(final InputProcessorChain inputProcessorChain, final XMLSecurityProperties securityProperties,
                       Deque<XMLEvent> eventQueue, Integer index) throws XMLSecurityException {

        @SuppressWarnings("unchecked")
        final SignatureType signatureType = ((JAXBElement<SignatureType>) parseStructure(eventQueue, index, securityProperties)).getValue();
        SecurityToken securityToken = verifySignedInfo(inputProcessorChain, securityProperties, signatureType, eventQueue, index);
        addSignatureReferenceInputProcessorToChain(inputProcessorChain, securityProperties, signatureType, securityToken);
    }

    protected abstract void addSignatureReferenceInputProcessorToChain(
            InputProcessorChain inputProcessorChain, XMLSecurityProperties securityProperties,
            SignatureType signatureType, SecurityToken securityToken) throws XMLSecurityException;

    protected SecurityToken verifySignedInfo(InputProcessorChain inputProcessorChain, XMLSecurityProperties securityProperties,
                                             SignatureType signatureType, Deque<XMLEvent> eventDeque, int index)
            throws XMLSecurityException {
        //todo reparse SignedInfo when custom canonicalization method is used
        //verify SignedInfo
        SignatureVerifier signatureVerifier = newSignatureVerifier(inputProcessorChain, securityProperties, signatureType);

        Iterator<XMLEvent> iterator = eventDeque.descendingIterator();
        //skip to <Signature> Element
        int i = 0;
        while (i < index) {
            iterator.next();
            i++;
        }

        try {
            boolean verifyElement = false;
            while (iterator.hasNext()) {
                XMLEvent xmlEvent = iterator.next();
                if (xmlEvent.isStartElement() && xmlEvent.asStartElement().getName().equals(XMLSecurityConstants.TAG_dsig_SignedInfo)) {
                    verifyElement = true;
                } else if (xmlEvent.isEndElement() && xmlEvent.asEndElement().getName().equals(XMLSecurityConstants.TAG_dsig_SignedInfo)) {
                    signatureVerifier.processEvent(xmlEvent);
                    break;
                }
                if (verifyElement) {
                    signatureVerifier.processEvent(xmlEvent);
                }
            }
        } catch (XMLStreamException e) {
            throw new XMLSecurityException(XMLSecurityException.ErrorCode.FAILED_CHECK, e);
        }
        signatureVerifier.doFinal();
        return signatureVerifier.getSecurityToken();
    }

    protected abstract SignatureVerifier newSignatureVerifier(InputProcessorChain inputProcessorChain,
                                                              XMLSecurityProperties securityProperties,
                                                              final SignatureType signatureType) throws XMLSecurityException;

/*
    <ds:Signature xmlns:ds="http://www.w3.org/2000/09/xmldsig#" Id="Signature-1022834285">
        <ds:SignedInfo>
            <ds:CanonicalizationMethod Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#" />
            <ds:SignatureMethod Algorithm="http://www.w3.org/2000/09/xmldsig#rsa-sha1" />
            <ds:Reference URI="#id-1612925417">
                <ds:Transforms>
                    <ds:Transform Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#" />
                </ds:Transforms>
                <ds:DigestMethod Algorithm="http://www.w3.org/2000/09/xmldsig#sha1" />
                <ds:DigestValue>cy/khx5N6UobCJ1EbX+qnrGID2U=</ds:DigestValue>
            </ds:Reference>
            <ds:Reference URI="#Timestamp-1106985890">
                <ds:Transforms>
                    <ds:Transform Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#" />
                </ds:Transforms>
                <ds:DigestMethod Algorithm="http://www.w3.org/2000/09/xmldsig#sha1" />
                <ds:DigestValue>+p5YRII6uvUdsJ7XLKkWx1CBewE=</ds:DigestValue>
            </ds:Reference>
        </ds:SignedInfo>
        <ds:SignatureValue>
            Izg1FlI9oa4gOon2vTXi7V0EpiyCUazECVGYflbXq7/3GF8ThKGDMpush/fo1I2NVjEFTfmT2WP/
            +ZG5N2jASFptrcGbsqmuLE5JbxUP1TVKb9SigKYcOQJJ8klzmVfPXnSiRZmIU+DUT2UXopWnGNFL
            TwY0Uxja4ZuI6U8m8Tg=
        </ds:SignatureValue>
        <ds:KeyInfo Id="KeyId-1043455692">
            <wsse:SecurityTokenReference xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd" xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd" wsu:Id="STRId-1008354042">
                <wsse:Reference xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd" URI="#CertId-3458500" ValueType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-x509-token-profile-1.0#X509v3" />
            </wsse:SecurityTokenReference>
        </ds:KeyInfo>
    </ds:Signature>
     */

    public class SignatureVerifier {

        private SignatureType signatureType;
        private SecurityToken securityToken;

        private SignerOutputStream signerOutputStream;
        private OutputStream bufferedSignerOutputStream;
        private Transformer transformer;
        private XMLSecurityProperties xmlSecurityProperties;

        public SignatureVerifier(SignatureType signatureType, SecurityContext securityContext,
                                 XMLSecurityProperties securityProperties) throws XMLSecurityException {
            this.signatureType = signatureType;

            KeyInfoType keyInfoType = signatureType.getKeyInfo();
            SecurityToken securityToken = SecurityTokenFactory.getInstance().getSecurityToken(keyInfoType,
                    securityProperties.getSignatureVerificationCrypto(), securityProperties.getCallbackHandler(),
                    securityContext);
            securityToken.verify();

            try {
                handleSecurityToken(securityToken);
                createSignatureAlgorithm(securityToken, signatureType);
            } catch (Exception e) {
                throw new XMLSecurityException(XMLSecurityException.ErrorCode.FAILED_CHECK, e);
            }
            this.securityToken = securityToken;
        }

        public SecurityToken getSecurityToken() {
            return securityToken;
        }

        protected void handleSecurityToken(SecurityToken securityToken) throws XMLSecurityException {
        }

        protected void createSignatureAlgorithm(SecurityToken securityToken, SignatureType signatureType)
                throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException,
                CertificateException, XMLSecurityException {

            Key verifyKey;
            final String algorithmURI = signatureType.getSignedInfo().getSignatureMethod().getAlgorithm();
            if (securityToken.isAsymmetric()) {
                verifyKey = securityToken.getPublicKey(algorithmURI, XMLSecurityConstants.Asym_Sig);
            } else {
                verifyKey = securityToken.getSecretKey(
                        algorithmURI, XMLSecurityConstants.Sym_Sig);
            }

            SignatureAlgorithm signatureAlgorithm =
                    SignatureAlgorithmFactory.getInstance().getSignatureAlgorithm(
                            algorithmURI);
            signatureAlgorithm.engineInitVerify(verifyKey);
            signerOutputStream = new SignerOutputStream(signatureAlgorithm);
            bufferedSignerOutputStream = new BufferedOutputStream(signerOutputStream);

            try {
                final CanonicalizationMethodType canonicalizationMethodType = signatureType.getSignedInfo().getCanonicalizationMethod();
                InclusiveNamespaces inclusiveNamespacesType =
                        XMLSecurityUtils.getQNameType(
                                canonicalizationMethodType.getContent(),
                                XMLSecurityConstants.TAG_c14nExcl_InclusiveNamespaces
                        );
                List<String> inclusiveNamespaces = inclusiveNamespacesType != null ? inclusiveNamespacesType.getPrefixList() : null;
                transformer = XMLSecurityUtils.getTransformer(
                        inclusiveNamespaces,
                        this.bufferedSignerOutputStream,
                        canonicalizationMethodType.getAlgorithm());
            } catch (NoSuchMethodException e) {
                throw new XMLSecurityException(XMLSecurityException.ErrorCode.FAILED_CHECK, e);
            } catch (InstantiationException e) {
                throw new XMLSecurityException(XMLSecurityException.ErrorCode.FAILED_CHECK, e);
            } catch (IllegalAccessException e) {
                throw new XMLSecurityException(XMLSecurityException.ErrorCode.FAILED_CHECK, e);
            } catch (InvocationTargetException e) {
                throw new XMLSecurityException(XMLSecurityException.ErrorCode.FAILED_CHECK, e);
            }
        }

        protected void processEvent(XMLEvent xmlEvent) throws XMLStreamException {
            transformer.transform(xmlEvent);
        }

        protected void doFinal() throws XMLSecurityException {
            try {
                bufferedSignerOutputStream.close();
                if (!signerOutputStream.verify(signatureType.getSignatureValue().getValue())) {
                    throw new XMLSecurityException(XMLSecurityException.ErrorCode.FAILED_CHECK);
                }
            } catch (IOException e) {
                throw new XMLSecurityException(XMLSecurityException.ErrorCode.FAILED_CHECK, e);
            }
        }
    }
}
