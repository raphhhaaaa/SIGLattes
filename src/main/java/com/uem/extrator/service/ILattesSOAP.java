package com.uem.extrator.service;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.xml.bind.annotation.XmlElement;

@WebService(targetNamespace = "http://ws.servico.repositorio.cnpq.br/", name = "WSCurriculo")
@SOAPBinding(style = SOAPBinding.Style.DOCUMENT)
public interface ILattesSOAP {

    // Metodo para baixar o currículo (Usa apenas o ID)
    @WebMethod
    byte[] getCurriculoCompactado(@WebParam(name = "id") String id);

    // Metodo para descobrir o ID (Usa CPF OU Nome + Data) - 3 Argumentos Obrigatórios
    @WebMethod
    String getIdentificadorCNPq(@WebParam(name = "cpf") @XmlElement(nillable = true) String cpf,
                                @WebParam(name = "nomeCompleto") @XmlElement(nillable = true) String nomeCompleto,
                                @WebParam(name = "dataNascimento") @XmlElement(nillable = true) String dataNascimento);
    @WebMethod
    String getDataAtualizacaoCV(@WebParam(name = "id") String id);

    @WebMethod
    String getOcorrenciaCV(@WebParam(name = "id") String id);
}