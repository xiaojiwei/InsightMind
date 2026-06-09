// Generated from java-escape by ANTLR 4.11.1
package com.graphinsight.indicator.lax.ifelse.iffunction;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue"})
public class IFElseParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.11.1", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, MEASTEXT=2, OR=3, AND=4, EQ=5, NEQ=6, GT=7, LT=8, GTEQ=9, LTEQ=10, 
		PLUS=11, MINUS=12, MULT=13, DIV=14, MOD=15, POW=16, NOT=17, SCOL=18, OPAR=19, 
		CPAR=20, OBRACE=21, CBRACE=22, TRUE=23, FALSE=24, NIL=25, IF=26, ELSE=27, 
		ID=28, INT=29, FLOAT=30, STRING=31, COMMENT=32, SPACE=33, OTHER=34;
	public static final int
		RULE_gra = 0, RULE_stat = 1, RULE_if_stat = 2, RULE_condition_block = 3, 
		RULE_stat_block = 4, RULE_expr = 5, RULE_atom = 6;
	private static String[] makeRuleNames() {
		return new String[] {
			"gra", "stat", "if_stat", "condition_block", "stat_block", "expr", "atom"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "','", null, "'||'", "'&&'", "'=='", "'!='", "'>'", "'<'", "'>='", 
			"'<='", "'+'", "'-'", "'*'", "'/'", "'%'", "'^'", "'!'", "';'", "'('", 
			"')'", "'{'", "'}'", "'true'", "'false'", "'nil'", "'if'", "'else'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, null, "MEASTEXT", "OR", "AND", "EQ", "NEQ", "GT", "LT", "GTEQ", 
			"LTEQ", "PLUS", "MINUS", "MULT", "DIV", "MOD", "POW", "NOT", "SCOL", 
			"OPAR", "CPAR", "OBRACE", "CBRACE", "TRUE", "FALSE", "NIL", "IF", "ELSE", 
			"ID", "INT", "FLOAT", "STRING", "COMMENT", "SPACE", "OTHER"
		};
	}
	private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;
	static {
		tokenNames = new String[_SYMBOLIC_NAMES.length];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = VOCABULARY.getLiteralName(i);
			if (tokenNames[i] == null) {
				tokenNames[i] = VOCABULARY.getSymbolicName(i);
			}

			if (tokenNames[i] == null) {
				tokenNames[i] = "<INVALID>";
			}
		}
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}

	@Override
	public String getGrammarFileName() { return "java-escape"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public IFElseParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@SuppressWarnings("CheckReturnValue")
	public static class GraContext extends ParserRuleContext {
		public List<StatContext> stat() {
			return getRuleContexts(StatContext.class);
		}
		public StatContext stat(int i) {
			return getRuleContext(StatContext.class,i);
		}
		public GraContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_gra; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).enterGra(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).exitGra(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof IFElseVisitor ) return ((IFElseVisitor<? extends T>)visitor).visitGra(this);
			else return visitor.visitChildren(this);
		}
	}

	public final GraContext gra() throws RecognitionException {
		GraContext _localctx = new GraContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_gra);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(15); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(14);
				stat();
				}
				}
				setState(17); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( ((_la) & ~0x3f) == 0 && ((1L << _la) & 21332889604L) != 0 );
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class StatContext extends ParserRuleContext {
		public Token OTHER;
		public If_statContext if_stat() {
			return getRuleContext(If_statContext.class,0);
		}
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public TerminalNode OTHER() { return getToken(IFElseParser.OTHER, 0); }
		public StatContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_stat; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).enterStat(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).exitStat(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof IFElseVisitor ) return ((IFElseVisitor<? extends T>)visitor).visitStat(this);
			else return visitor.visitChildren(this);
		}
	}

	public final StatContext stat() throws RecognitionException {
		StatContext _localctx = new StatContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_stat);
		try {
			setState(23);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case IF:
				enterOuterAlt(_localctx, 1);
				{
				setState(19);
				if_stat();
				}
				break;
			case MEASTEXT:
			case MINUS:
			case NOT:
			case OPAR:
			case TRUE:
			case FALSE:
			case NIL:
			case ID:
			case INT:
			case FLOAT:
			case STRING:
				enterOuterAlt(_localctx, 2);
				{
				setState(20);
				expr(0);
				}
				break;
			case OTHER:
				enterOuterAlt(_localctx, 3);
				{
				setState(21);
				((StatContext)_localctx).OTHER = match(OTHER);
				System.err.println("unknown char: " + (((StatContext)_localctx).OTHER!=null?((StatContext)_localctx).OTHER.getText():null));
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class If_statContext extends ParserRuleContext {
		public TerminalNode IF() { return getToken(IFElseParser.IF, 0); }
		public TerminalNode OPAR() { return getToken(IFElseParser.OPAR, 0); }
		public Condition_blockContext condition_block() {
			return getRuleContext(Condition_blockContext.class,0);
		}
		public List<Stat_blockContext> stat_block() {
			return getRuleContexts(Stat_blockContext.class);
		}
		public Stat_blockContext stat_block(int i) {
			return getRuleContext(Stat_blockContext.class,i);
		}
		public TerminalNode CPAR() { return getToken(IFElseParser.CPAR, 0); }
		public If_statContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_if_stat; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).enterIf_stat(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).exitIf_stat(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof IFElseVisitor ) return ((IFElseVisitor<? extends T>)visitor).visitIf_stat(this);
			else return visitor.visitChildren(this);
		}
	}

	public final If_statContext if_stat() throws RecognitionException {
		If_statContext _localctx = new If_statContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_if_stat);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(25);
			match(IF);
			setState(26);
			match(OPAR);
			setState(27);
			condition_block();
			setState(28);
			match(T__0);
			setState(29);
			stat_block();
			setState(30);
			match(T__0);
			setState(31);
			stat_block();
			setState(32);
			match(CPAR);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Condition_blockContext extends ParserRuleContext {
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public Condition_blockContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_condition_block; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).enterCondition_block(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).exitCondition_block(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof IFElseVisitor ) return ((IFElseVisitor<? extends T>)visitor).visitCondition_block(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Condition_blockContext condition_block() throws RecognitionException {
		Condition_blockContext _localctx = new Condition_blockContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_condition_block);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(34);
			expr(0);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Stat_blockContext extends ParserRuleContext {
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public Stat_blockContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_stat_block; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).enterStat_block(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).exitStat_block(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof IFElseVisitor ) return ((IFElseVisitor<? extends T>)visitor).visitStat_block(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Stat_blockContext stat_block() throws RecognitionException {
		Stat_blockContext _localctx = new Stat_blockContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_stat_block);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(36);
			expr(0);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ExprContext extends ParserRuleContext {
		public ExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_expr; }
	 
		public ExprContext() { }
		public void copyFrom(ExprContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class NotExprContext extends ExprContext {
		public TerminalNode NOT() { return getToken(IFElseParser.NOT, 0); }
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public NotExprContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).enterNotExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).exitNotExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof IFElseVisitor ) return ((IFElseVisitor<? extends T>)visitor).visitNotExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class UnaryMinusExprContext extends ExprContext {
		public TerminalNode MINUS() { return getToken(IFElseParser.MINUS, 0); }
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public UnaryMinusExprContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).enterUnaryMinusExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).exitUnaryMinusExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof IFElseVisitor ) return ((IFElseVisitor<? extends T>)visitor).visitUnaryMinusExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class BrExprContext extends ExprContext {
		public TerminalNode OPAR() { return getToken(IFElseParser.OPAR, 0); }
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public TerminalNode CPAR() { return getToken(IFElseParser.CPAR, 0); }
		public BrExprContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).enterBrExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).exitBrExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof IFElseVisitor ) return ((IFElseVisitor<? extends T>)visitor).visitBrExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class MultiplicationExprContext extends ExprContext {
		public Token op;
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public TerminalNode MULT() { return getToken(IFElseParser.MULT, 0); }
		public TerminalNode DIV() { return getToken(IFElseParser.DIV, 0); }
		public TerminalNode MOD() { return getToken(IFElseParser.MOD, 0); }
		public MultiplicationExprContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).enterMultiplicationExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).exitMultiplicationExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof IFElseVisitor ) return ((IFElseVisitor<? extends T>)visitor).visitMultiplicationExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class AtomExprContext extends ExprContext {
		public AtomContext atom() {
			return getRuleContext(AtomContext.class,0);
		}
		public AtomExprContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).enterAtomExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).exitAtomExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof IFElseVisitor ) return ((IFElseVisitor<? extends T>)visitor).visitAtomExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class OrExprContext extends ExprContext {
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public TerminalNode OR() { return getToken(IFElseParser.OR, 0); }
		public OrExprContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).enterOrExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).exitOrExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof IFElseVisitor ) return ((IFElseVisitor<? extends T>)visitor).visitOrExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class AdditiveExprContext extends ExprContext {
		public Token op;
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public TerminalNode PLUS() { return getToken(IFElseParser.PLUS, 0); }
		public TerminalNode MINUS() { return getToken(IFElseParser.MINUS, 0); }
		public AdditiveExprContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).enterAdditiveExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).exitAdditiveExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof IFElseVisitor ) return ((IFElseVisitor<? extends T>)visitor).visitAdditiveExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class PowExprContext extends ExprContext {
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public TerminalNode POW() { return getToken(IFElseParser.POW, 0); }
		public PowExprContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).enterPowExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).exitPowExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof IFElseVisitor ) return ((IFElseVisitor<? extends T>)visitor).visitPowExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class RelationalExprContext extends ExprContext {
		public Token op;
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public TerminalNode LTEQ() { return getToken(IFElseParser.LTEQ, 0); }
		public TerminalNode GTEQ() { return getToken(IFElseParser.GTEQ, 0); }
		public TerminalNode LT() { return getToken(IFElseParser.LT, 0); }
		public TerminalNode GT() { return getToken(IFElseParser.GT, 0); }
		public RelationalExprContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).enterRelationalExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).exitRelationalExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof IFElseVisitor ) return ((IFElseVisitor<? extends T>)visitor).visitRelationalExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class EqualityExprContext extends ExprContext {
		public Token op;
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public TerminalNode EQ() { return getToken(IFElseParser.EQ, 0); }
		public TerminalNode NEQ() { return getToken(IFElseParser.NEQ, 0); }
		public EqualityExprContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).enterEqualityExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).exitEqualityExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof IFElseVisitor ) return ((IFElseVisitor<? extends T>)visitor).visitEqualityExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class AndExprContext extends ExprContext {
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public TerminalNode AND() { return getToken(IFElseParser.AND, 0); }
		public AndExprContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).enterAndExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).exitAndExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof IFElseVisitor ) return ((IFElseVisitor<? extends T>)visitor).visitAndExpr(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ExprContext expr() throws RecognitionException {
		return expr(0);
	}

	private ExprContext expr(int _p) throws RecognitionException {
		ParserRuleContext _parentctx = _ctx;
		int _parentState = getState();
		ExprContext _localctx = new ExprContext(_ctx, _parentState);
		ExprContext _prevctx = _localctx;
		int _startState = 10;
		enterRecursionRule(_localctx, 10, RULE_expr, _p);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(48);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,2,_ctx) ) {
			case 1:
				{
				_localctx = new BrExprContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;

				setState(39);
				match(OPAR);
				setState(40);
				expr(0);
				setState(41);
				match(CPAR);
				}
				break;
			case 2:
				{
				_localctx = new UnaryMinusExprContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(43);
				match(MINUS);
				setState(44);
				expr(9);
				}
				break;
			case 3:
				{
				_localctx = new NotExprContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(45);
				match(NOT);
				setState(46);
				expr(8);
				}
				break;
			case 4:
				{
				_localctx = new AtomExprContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(47);
				atom();
				}
				break;
			}
			_ctx.stop = _input.LT(-1);
			setState(73);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,4,_ctx);
			while ( _alt!=2 && _alt!= ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					setState(71);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,3,_ctx) ) {
					case 1:
						{
						_localctx = new PowExprContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(50);
						if (!(precpred(_ctx, 10))) throw new FailedPredicateException(this, "precpred(_ctx, 10)");
						setState(51);
						match(POW);
						setState(52);
						expr(11);
						}
						break;
					case 2:
						{
						_localctx = new MultiplicationExprContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(53);
						if (!(precpred(_ctx, 7))) throw new FailedPredicateException(this, "precpred(_ctx, 7)");
						setState(54);
						((MultiplicationExprContext)_localctx).op = _input.LT(1);
						_la = _input.LA(1);
						if ( !(((_la) & ~0x3f) == 0 && ((1L << _la) & 57344L) != 0) ) {
							((MultiplicationExprContext)_localctx).op = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(55);
						expr(8);
						}
						break;
					case 3:
						{
						_localctx = new AdditiveExprContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(56);
						if (!(precpred(_ctx, 6))) throw new FailedPredicateException(this, "precpred(_ctx, 6)");
						setState(57);
						((AdditiveExprContext)_localctx).op = _input.LT(1);
						_la = _input.LA(1);
						if ( !(_la==PLUS || _la==MINUS) ) {
							((AdditiveExprContext)_localctx).op = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(58);
						expr(7);
						}
						break;
					case 4:
						{
						_localctx = new RelationalExprContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(59);
						if (!(precpred(_ctx, 5))) throw new FailedPredicateException(this, "precpred(_ctx, 5)");
						setState(60);
						((RelationalExprContext)_localctx).op = _input.LT(1);
						_la = _input.LA(1);
						if ( !(((_la) & ~0x3f) == 0 && ((1L << _la) & 1920L) != 0) ) {
							((RelationalExprContext)_localctx).op = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(61);
						expr(6);
						}
						break;
					case 5:
						{
						_localctx = new EqualityExprContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(62);
						if (!(precpred(_ctx, 4))) throw new FailedPredicateException(this, "precpred(_ctx, 4)");
						setState(63);
						((EqualityExprContext)_localctx).op = _input.LT(1);
						_la = _input.LA(1);
						if ( !(_la==EQ || _la==NEQ) ) {
							((EqualityExprContext)_localctx).op = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(64);
						expr(5);
						}
						break;
					case 6:
						{
						_localctx = new AndExprContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(65);
						if (!(precpred(_ctx, 3))) throw new FailedPredicateException(this, "precpred(_ctx, 3)");
						setState(66);
						match(AND);
						setState(67);
						expr(4);
						}
						break;
					case 7:
						{
						_localctx = new OrExprContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(68);
						if (!(precpred(_ctx, 2))) throw new FailedPredicateException(this, "precpred(_ctx, 2)");
						setState(69);
						match(OR);
						setState(70);
						expr(3);
						}
						break;
					}
					} 
				}
				setState(75);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,4,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			unrollRecursionContexts(_parentctx);
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class AtomContext extends ParserRuleContext {
		public AtomContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_atom; }
	 
		public AtomContext() { }
		public void copyFrom(AtomContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ParExprContext extends AtomContext {
		public TerminalNode OPAR() { return getToken(IFElseParser.OPAR, 0); }
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public TerminalNode CPAR() { return getToken(IFElseParser.CPAR, 0); }
		public ParExprContext(AtomContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).enterParExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).exitParExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof IFElseVisitor ) return ((IFElseVisitor<? extends T>)visitor).visitParExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class BooleanAtomContext extends AtomContext {
		public TerminalNode TRUE() { return getToken(IFElseParser.TRUE, 0); }
		public TerminalNode FALSE() { return getToken(IFElseParser.FALSE, 0); }
		public BooleanAtomContext(AtomContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).enterBooleanAtom(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).exitBooleanAtom(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof IFElseVisitor ) return ((IFElseVisitor<? extends T>)visitor).visitBooleanAtom(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class IdAtomContext extends AtomContext {
		public TerminalNode ID() { return getToken(IFElseParser.ID, 0); }
		public IdAtomContext(AtomContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).enterIdAtom(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).exitIdAtom(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof IFElseVisitor ) return ((IFElseVisitor<? extends T>)visitor).visitIdAtom(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class StringAtomContext extends AtomContext {
		public TerminalNode STRING() { return getToken(IFElseParser.STRING, 0); }
		public StringAtomContext(AtomContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).enterStringAtom(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).exitStringAtom(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof IFElseVisitor ) return ((IFElseVisitor<? extends T>)visitor).visitStringAtom(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class MeasContext extends AtomContext {
		public TerminalNode MEASTEXT() { return getToken(IFElseParser.MEASTEXT, 0); }
		public MeasContext(AtomContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).enterMeas(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).exitMeas(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof IFElseVisitor ) return ((IFElseVisitor<? extends T>)visitor).visitMeas(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class NilAtomContext extends AtomContext {
		public TerminalNode NIL() { return getToken(IFElseParser.NIL, 0); }
		public NilAtomContext(AtomContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).enterNilAtom(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).exitNilAtom(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof IFElseVisitor ) return ((IFElseVisitor<? extends T>)visitor).visitNilAtom(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class NumberAtomContext extends AtomContext {
		public TerminalNode INT() { return getToken(IFElseParser.INT, 0); }
		public TerminalNode FLOAT() { return getToken(IFElseParser.FLOAT, 0); }
		public NumberAtomContext(AtomContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).enterNumberAtom(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof IFElseListener ) ((IFElseListener)listener).exitNumberAtom(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof IFElseVisitor ) return ((IFElseVisitor<? extends T>)visitor).visitNumberAtom(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AtomContext atom() throws RecognitionException {
		AtomContext _localctx = new AtomContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_atom);
		int _la;
		try {
			setState(86);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case OPAR:
				_localctx = new ParExprContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(76);
				match(OPAR);
				setState(77);
				expr(0);
				setState(78);
				match(CPAR);
				}
				break;
			case INT:
			case FLOAT:
				_localctx = new NumberAtomContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(80);
				_la = _input.LA(1);
				if ( !(_la==INT || _la==FLOAT) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				}
				break;
			case TRUE:
			case FALSE:
				_localctx = new BooleanAtomContext(_localctx);
				enterOuterAlt(_localctx, 3);
				{
				setState(81);
				_la = _input.LA(1);
				if ( !(_la==TRUE || _la==FALSE) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				}
				break;
			case ID:
				_localctx = new IdAtomContext(_localctx);
				enterOuterAlt(_localctx, 4);
				{
				setState(82);
				match(ID);
				}
				break;
			case MEASTEXT:
				_localctx = new MeasContext(_localctx);
				enterOuterAlt(_localctx, 5);
				{
				setState(83);
				match(MEASTEXT);
				}
				break;
			case STRING:
				_localctx = new StringAtomContext(_localctx);
				enterOuterAlt(_localctx, 6);
				{
				setState(84);
				match(STRING);
				}
				break;
			case NIL:
				_localctx = new NilAtomContext(_localctx);
				enterOuterAlt(_localctx, 7);
				{
				setState(85);
				match(NIL);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public boolean sempred(RuleContext _localctx, int ruleIndex, int predIndex) {
		switch (ruleIndex) {
		case 5:
			return expr_sempred((ExprContext)_localctx, predIndex);
		}
		return true;
	}
	private boolean expr_sempred(ExprContext _localctx, int predIndex) {
		switch (predIndex) {
		case 0:
			return precpred(_ctx, 10);
		case 1:
			return precpred(_ctx, 7);
		case 2:
			return precpred(_ctx, 6);
		case 3:
			return precpred(_ctx, 5);
		case 4:
			return precpred(_ctx, 4);
		case 5:
			return precpred(_ctx, 3);
		case 6:
			return precpred(_ctx, 2);
		}
		return true;
	}

	public static final String _serializedATN =
		"\u0004\u0001\"Y\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002"+
		"\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004\u0002"+
		"\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0001\u0000\u0004\u0000\u0010"+
		"\b\u0000\u000b\u0000\f\u0000\u0011\u0001\u0001\u0001\u0001\u0001\u0001"+
		"\u0001\u0001\u0003\u0001\u0018\b\u0001\u0001\u0002\u0001\u0002\u0001\u0002"+
		"\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002"+
		"\u0001\u0003\u0001\u0003\u0001\u0004\u0001\u0004\u0001\u0005\u0001\u0005"+
		"\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005"+
		"\u0001\u0005\u0001\u0005\u0003\u00051\b\u0005\u0001\u0005\u0001\u0005"+
		"\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005"+
		"\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005"+
		"\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005"+
		"\u0001\u0005\u0005\u0005H\b\u0005\n\u0005\f\u0005K\t\u0005\u0001\u0006"+
		"\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006"+
		"\u0001\u0006\u0001\u0006\u0001\u0006\u0003\u0006W\b\u0006\u0001\u0006"+
		"\u0000\u0001\n\u0007\u0000\u0002\u0004\u0006\b\n\f\u0000\u0006\u0001\u0000"+
		"\r\u000f\u0001\u0000\u000b\f\u0001\u0000\u0007\n\u0001\u0000\u0005\u0006"+
		"\u0001\u0000\u001d\u001e\u0001\u0000\u0017\u0018d\u0000\u000f\u0001\u0000"+
		"\u0000\u0000\u0002\u0017\u0001\u0000\u0000\u0000\u0004\u0019\u0001\u0000"+
		"\u0000\u0000\u0006\"\u0001\u0000\u0000\u0000\b$\u0001\u0000\u0000\u0000"+
		"\n0\u0001\u0000\u0000\u0000\fV\u0001\u0000\u0000\u0000\u000e\u0010\u0003"+
		"\u0002\u0001\u0000\u000f\u000e\u0001\u0000\u0000\u0000\u0010\u0011\u0001"+
		"\u0000\u0000\u0000\u0011\u000f\u0001\u0000\u0000\u0000\u0011\u0012\u0001"+
		"\u0000\u0000\u0000\u0012\u0001\u0001\u0000\u0000\u0000\u0013\u0018\u0003"+
		"\u0004\u0002\u0000\u0014\u0018\u0003\n\u0005\u0000\u0015\u0016\u0005\""+
		"\u0000\u0000\u0016\u0018\u0006\u0001\uffff\uffff\u0000\u0017\u0013\u0001"+
		"\u0000\u0000\u0000\u0017\u0014\u0001\u0000\u0000\u0000\u0017\u0015\u0001"+
		"\u0000\u0000\u0000\u0018\u0003\u0001\u0000\u0000\u0000\u0019\u001a\u0005"+
		"\u001a\u0000\u0000\u001a\u001b\u0005\u0013\u0000\u0000\u001b\u001c\u0003"+
		"\u0006\u0003\u0000\u001c\u001d\u0005\u0001\u0000\u0000\u001d\u001e\u0003"+
		"\b\u0004\u0000\u001e\u001f\u0005\u0001\u0000\u0000\u001f \u0003\b\u0004"+
		"\u0000 !\u0005\u0014\u0000\u0000!\u0005\u0001\u0000\u0000\u0000\"#\u0003"+
		"\n\u0005\u0000#\u0007\u0001\u0000\u0000\u0000$%\u0003\n\u0005\u0000%\t"+
		"\u0001\u0000\u0000\u0000&\'\u0006\u0005\uffff\uffff\u0000\'(\u0005\u0013"+
		"\u0000\u0000()\u0003\n\u0005\u0000)*\u0005\u0014\u0000\u0000*1\u0001\u0000"+
		"\u0000\u0000+,\u0005\f\u0000\u0000,1\u0003\n\u0005\t-.\u0005\u0011\u0000"+
		"\u0000.1\u0003\n\u0005\b/1\u0003\f\u0006\u00000&\u0001\u0000\u0000\u0000"+
		"0+\u0001\u0000\u0000\u00000-\u0001\u0000\u0000\u00000/\u0001\u0000\u0000"+
		"\u00001I\u0001\u0000\u0000\u000023\n\n\u0000\u000034\u0005\u0010\u0000"+
		"\u00004H\u0003\n\u0005\u000b56\n\u0007\u0000\u000067\u0007\u0000\u0000"+
		"\u00007H\u0003\n\u0005\b89\n\u0006\u0000\u00009:\u0007\u0001\u0000\u0000"+
		":H\u0003\n\u0005\u0007;<\n\u0005\u0000\u0000<=\u0007\u0002\u0000\u0000"+
		"=H\u0003\n\u0005\u0006>?\n\u0004\u0000\u0000?@\u0007\u0003\u0000\u0000"+
		"@H\u0003\n\u0005\u0005AB\n\u0003\u0000\u0000BC\u0005\u0004\u0000\u0000"+
		"CH\u0003\n\u0005\u0004DE\n\u0002\u0000\u0000EF\u0005\u0003\u0000\u0000"+
		"FH\u0003\n\u0005\u0003G2\u0001\u0000\u0000\u0000G5\u0001\u0000\u0000\u0000"+
		"G8\u0001\u0000\u0000\u0000G;\u0001\u0000\u0000\u0000G>\u0001\u0000\u0000"+
		"\u0000GA\u0001\u0000\u0000\u0000GD\u0001\u0000\u0000\u0000HK\u0001\u0000"+
		"\u0000\u0000IG\u0001\u0000\u0000\u0000IJ\u0001\u0000\u0000\u0000J\u000b"+
		"\u0001\u0000\u0000\u0000KI\u0001\u0000\u0000\u0000LM\u0005\u0013\u0000"+
		"\u0000MN\u0003\n\u0005\u0000NO\u0005\u0014\u0000\u0000OW\u0001\u0000\u0000"+
		"\u0000PW\u0007\u0004\u0000\u0000QW\u0007\u0005\u0000\u0000RW\u0005\u001c"+
		"\u0000\u0000SW\u0005\u0002\u0000\u0000TW\u0005\u001f\u0000\u0000UW\u0005"+
		"\u0019\u0000\u0000VL\u0001\u0000\u0000\u0000VP\u0001\u0000\u0000\u0000"+
		"VQ\u0001\u0000\u0000\u0000VR\u0001\u0000\u0000\u0000VS\u0001\u0000\u0000"+
		"\u0000VT\u0001\u0000\u0000\u0000VU\u0001\u0000\u0000\u0000W\r\u0001\u0000"+
		"\u0000\u0000\u0006\u0011\u00170GIV";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}