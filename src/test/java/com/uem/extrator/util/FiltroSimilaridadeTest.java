package com.uem.extrator.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class FiltroSimilaridadeTest {

    @Test
    public void testPalavrasIdenticas() {
        int distancia = FiltroSimilaridade.distanciaLevenshtein("Universidade Estadual de Maringá", "Universidade Estadual de Maringá");
        assertEquals(0, distancia, "A distancia entre as palavras deve ser 0.");
    }


    @Test
    public void testDistanciaRelativa() {
        float distanciaRelativa = FiltroSimilaridade.distanciaRelativa("Universidade Estadual de Maringá", "Universidade Estadual de Maringa") * 100;
        System.out.println("A distancia relativa entre as strings é de: " + distanciaRelativa + "%.");
        System.out.println("Elas são: " + (100 - distanciaRelativa) + "% semelhantes." );
        assertEquals(3.125, distanciaRelativa);
    }

    @Test
    public void testEhMesmaInstituicao() {
        // --- CASOS UEM ---
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("UNIVERSIDADE ESTADUAL DE MARINGÁ", "UNIVERSIDADE ESTADUAL DE MARINGÁ"));
        assertFalse(FiltroSimilaridade.isMesmaInstituicao("UEM", "USP"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringa", "Universidade Estadual de Maringá"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringa", "Universidade Estadual de Maringá - Campuns Cianorte"));
        
        // --- CASOS GENÉRICOS (OUTRAS INSTITUIÇÕES) ---
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade de São Paulo", "USP"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade de São Paulo", "Universidade de Sao Paulo - USP"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Campinas", "UNICAMP"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Campinas", "Universidade Estadual de Campinas - UNICAMP"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Federal do Paraná", "UFPR"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Federal do Paraná", "Universidade Federal do Parana - Setor de Tecnologia"));
        
        // --- CASOS UEM ORIGINAIS (EXTENSO) ---
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Universidade Estadual de Maringá-pR"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Universidade Estadual de Maringá Departamento de Fundamentos da Educação"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Fundação Universidade Estadual de Maringá"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "UEM - Universidade Estadual de Maringá"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Universidade Estadual de Maringá/Universidade Aberta do Brasil"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Universidade Estadual de Ponta Grossa/ Universidade Estadual de Maringá"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "(UEM)Universidade Estadual de Maringá"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Editora da Universidade Estadual de Maringá"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Editora da Universidade Estadual de Maringá-EDUEM"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Associação dos Docentes da Universidade Estadual de Maringá"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Colégio de Aplicação Pedagógica da Universidade Estadual de Maringá"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Instituto de Línguas da Universidade Estadual de Maringá Uem"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Universidade Estadual de Maringá - Campus Umuarama"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Universidade Estadual de Maringá Instituto de Línguas"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Programa de Pós Graduação em Economia da Universidade Estadual de Maringá"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "UEM - Fundação Universidade Estadual de Maringá"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Museu Dinâmico Interdisciplinar da Universidade Estadual de Maringá"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Universidade Estadual de Maringá Hospital Universitário de Maringá"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Universidade Estadual de Maringá-EAD"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Universidade Estadual de Maringá- Agronomia"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Universidade Estadual de Maringá- Zootecnia e Agronomia"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Universidade Estadual de Maringá,"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Núcleo de Apoio Contábil e Fiscal - Universidade Estadual de Maringá"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Universidade Estadual de Maringá - Campus de Cianorte"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Associação dos Funcionários da Universidade Estadual de Maringá"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Laboratório de Microbiologia da Universidade Estadual de Maringá"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "SAJ   Serviço de Assistência Judiciária da Universidade Estadual de Maringá"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Parthenon Empresa Júnior - Universidade Estadual de Maringá"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "EDUEM - Editora da Universidade Estadual de Maringá"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Universidade Estadual de Maringá - UEM"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Universidade Estadual de Maringá- PROFHISTORIA"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Universidade Estadual de Maringá - Laboratório de Arqueologia, Etnologia e"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "PARFOR - Universidade Estadual de Maringá"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Universidade Estadual de Maringá UEM"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "UNIVERSIDADE ESTADUAL DE MARINGÁ - CAMPUS UMUARAMA - PR."));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Editora da Universidade Estadual de Maringá - EDUEM"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Núcleo de Ensino à Distância Universidade Estadual de Maringá"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Universidade Estadual de Maringá - CRG"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Universidade Estadual de Maringá - Hospital Universitário de Maringá"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Pró Reitoria de Extensão da Universidade Estadual de Maringá"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Laboratório de Saneamento e Meio Ambiente/Universidade Estadual de Maringá"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Fundação Universidade Estadual de Maringá Paraná"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Universidade Estadual de Maringá, Campus Regional de Umuarama"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Departamento de Física da Universidade Estadual de Maringá"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Universidade Estadual de Maringá - Departamento de Engenharia Química"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Universidade Estadual de Maringá - Departamento de Matemática"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Universidade Estadual de Maringá / PR"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Fundação Universidade Estadual de Maringá Pr"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Universidade Estadual de Maringá/Campus Cianorte"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Universidade Estadual de Maringá  P.R"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Revista Acta Scientiarum-Universidade Estadual de Maringá"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "(UEM) Fundação Universidade Estadual de Maringá (programa PIBIC CNPqUEM)"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Universidade Estadual de Maringá/Centro de Ciências da Saúde"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "EDUEM Editora Universidade Estadual de Maringá"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Universidade Estadual de Maringá / Universidade Federal de Viçosa"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Departamento de Informática - Universidade Estadual de Maringá"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Universidade Estadual de Londrina e Universidade Estadual de Maringá"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "PIBIC Universidade Estadual de Maringá"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Pós Doutorado em Educação - Universidade Estadual de Maringá"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Universidade Estadual de Maringá - Câmpus Umuarama-PR"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "O Direito Pensa - Universidade Estadual de Maringá (UEM)"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Universidade Estadual de Maringá (Campus Regional de Goioerê)"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Universidade Estadual de Maringá (Campus Regional de Umuarama)"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Hospital Universitário de Maringá - Universidade Estadual de Maringá"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Universidade Estadual de Maringá - UEM (Goioerê)"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "UNIVERSIDADE ESTADUAL DE MARINGÁ PR"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Nucleo de Inovação TEcnologica da Universidade Estadual de Maringá"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Universidade Estadual de Maringá/ Campus Regional de Cianorte"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Núcleo de Educação a Distancia da Universidade Estadual de Maringá"));
        assertTrue(FiltroSimilaridade.isMesmaInstituicao("Universidade Estadual de Maringá", "Colégio de Aplicação Pedagógica - CAP da Universidade Estadual de Maringá"));
    }
}



