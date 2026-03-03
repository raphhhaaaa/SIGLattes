package com.uem.extrator.service;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.xml.bind.annotation.XmlElement;

@WebService(targetNamespace = "http://ws.servico.repositorio.cnpq.br/", name = "WSCurriculo")
@SOAPBinding(style = SOAPBinding.Style.RPC)
public interface ILattesSOAP {

    // Metodo para baixar o currículo (Usa apenas o ID)
    @WebMethod
    byte[] getCurriculoCompactado(@WebParam(name = "id") String id);

    // Metodo para descobrir o ID (Usa CPF OU Nome + Data) - 3 Argumentos Obrigatórios
    @WebMethod
    String getIdentificadorCNPq(@WebParam(name = "cpf") String cpf,
                                @WebParam(name = "nomeCompleto") String nomeCompleto,
                                @WebParam(name = "dataNascimento") String dataNascimento);
    @WebMethod
    String getDataAtualizacaoCV(@WebParam(name = "id") String id);
}