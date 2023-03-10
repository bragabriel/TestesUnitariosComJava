package br.ce.wcaquino.servicos;

import br.ce.wcaquino.daos.LocacaoDAO;
import br.ce.wcaquino.entidades.Filme;
import br.ce.wcaquino.entidades.Locacao;
import br.ce.wcaquino.entidades.Usuario;
import br.ce.wcaquino.exceptions.FilmeSemEstoqueException;
import br.ce.wcaquino.exceptions.LocadoraException;
import br.ce.wcaquino.utils.DataUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static br.ce.wcaquino.utils.DataUtils.adicionarDias;

public class LocacaoService {

	private LocacaoDAO dao;
	private SPCService spcService;
	private EmailService emailService;

	public Locacao alugarFilme(Usuario usuario, List<Filme> filmes) throws FilmeSemEstoqueException, LocadoraException {

		if(usuario == null){
			throw new LocadoraException("Usuário vazio");
		}

		if(filmes == null || filmes.isEmpty()){
			throw new LocadoraException("Filme vazio");
		}

		for(Filme itens: filmes){
			if(itens.getEstoque() == 0){
				throw new FilmeSemEstoqueException("Filme sem estoque");
			}
		}

		boolean negativado;

		try {
			negativado = spcService.possuiNegativacao(usuario);
		} catch (Exception e) {
			throw new LocadoraException("Problemas com SPC, tente novamente");
		}

		if(negativado) {
			throw new LocadoraException("Usuário Negativado");
		}

		Locacao locacao = new Locacao();
		locacao.setFilmes(filmes);
		locacao.setUsuario(usuario);
		locacao.setDataLocacao(new Date());

		Double valorTotal = 0d;

		for(int i = 0; i < filmes.size(); i++){
			Filme filme = filmes.get(i);
			Double valorFilme = filme.getPrecoLocacao();

			switch (i){
				case 2: //3° interação
					valorFilme = valorFilme * 0.75;
					break;

				case 3: //4° interação
					valorFilme = valorFilme * 0.50;
					break;

				case 4://5° interação
					valorFilme = valorFilme * 0.25;
					break;

				case 5:	//6° interação
					valorFilme = valorFilme * 0.0;
					break;
			}

			valorTotal += valorFilme;
		}
		locacao.setValor(valorTotal);

		//Entrega no dia seguinte
		Date dataEntrega = new Date();
		dataEntrega = adicionarDias(dataEntrega, 1);
		if(DataUtils.verificarDiaSemana(dataEntrega, Calendar.SUNDAY)){
			dataEntrega = adicionarDias(dataEntrega, 1);
		}
		locacao.setDataRetorno(dataEntrega);

		//Salvando a locacao...	
		dao.salvar(locacao);
		
		return locacao;
	}

	public void notificarAtrasos(){
		List<Locacao> locacoes = dao.obterLocacoesPendentes();

		for(Locacao locacao: locacoes){
			if(locacao.getDataLocacao().before(new Date())){
				emailService.notificarAtraso(locacao.getUsuario());
			}
		}
	}

	public void prorrogarLocacao(Locacao locacao, int dias){
		Locacao novaLocacao = new Locacao();
		novaLocacao.setUsuario(locacao.getUsuario());
		novaLocacao.setFilmes(locacao.getFilmes());
		novaLocacao.setDataLocacao(locacao.getDataLocacao());
		novaLocacao.setDataRetorno(locacao.getDataRetorno());
		novaLocacao.setValor(locacao.getValor() * dias);
		dao.salvar(novaLocacao);
	}
}