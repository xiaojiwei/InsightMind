// Generated from java-escape by ANTLR 4.11.1
package com.graphinsight.indicator.lax.ifelse.mubak;
import org.antlr.v4.runtime.FailedPredicateException;
import org.antlr.v4.runtime.NoViableAltException;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.RuntimeMetaData;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.VocabularyImpl;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.atn.ATNDeserializer;
import org.antlr.v4.runtime.atn.ParserATNSimulator;
import org.antlr.v4.runtime.atn.PredictionContextCache;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.List;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue"})
public class MUParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.11.1", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		OR=1, AND=2, EQ=3, NEQ=4, GT=5, LT=6, GTEQ=7, LTEQ=8, PLUS=9, MINUS=10, 
		MULT=11, DIV=12, MOD=13, POW=14, NOT=15, SCOL=16, ASSIGN=17, OPAR=18, 
		CPAR=19, OBRACE=20, CBRACE=21, TRUE=22, FALSE=23, NIL=24, IF=25, ELSE=26, 
		WHILE=27, LOG=28, ID=29, INT=30, FLOAT=31, STRING=32, COMMENT=33, SPACE=34, 
		OTHER=35;
	public static final int
		RULE_parse = 0, RULE_block = 1, RULE_stat = 2, RULE_assignment = 3, RULE_if_stat = 4, 
		RULE_condition_block = 5, RULE_stat_block = 6, RULE_while_stat = 7, RULE_log = 8, 
		RULE_expr = 9, RULE_atom = 10;
	private static String[] makeRuleNames() {
		return new String[] {
			"parse", "block", "stat", "assignment", "if_stat", "condition_block", 
			"stat_block", "while_stat", "log", "expr", "atom"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'||'", "'&&'", "'=='", "'!='", "'>'", "'<'", "'>='", "'<='", "'+'", 
			"'-'", "'*'", "'/'", "'%'", "'^'", "'!'", "';'", "'='", "'('", "')'", 
			"'{'", "'}'", "'true'", "'false'", "'nil'", "'if'", "'else'", "'while'", 
			"'log'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, "OR", "AND", "EQ", "NEQ", "GT", "LT", "GTEQ", "LTEQ", "PLUS", "MINUS", 
			"MULT", "DIV", "MOD", "POW", "NOT", "SCOL", "ASSIGN", "OPAR", "CPAR", 
			"OBRACE", "CBRACE", "TRUE", "FALSE", "NIL", "IF", "ELSE", "WHILE", "LOG", 
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

	public MUParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ParseContext extends ParserRuleContext {
		public BlockContext block() {
			return getRuleContext(BlockContext.class,0);
		}
		public TerminalNode EOF() { return getToken(MUParser.EOF, 0); }
		public ParseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_parse; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).enterParse(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).exitParse(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor) return ((MUVisitor<? extends T>)visitor).visitParse(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ParseContext parse() throws RecognitionException {
		ParseContext _localctx = new ParseContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_parse);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(22);
			block();
			setState(23);
			match(EOF);
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
	public static class BlockContext extends ParserRuleContext {
		public List<StatContext> stat() {
			return getRuleContexts(StatContext.class);
		}
		public StatContext stat(int i) {
			return getRuleContext(StatContext.class,i);
		}
		public BlockContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_block; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).enterBlock(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).exitBlock(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor) return ((MUVisitor<? extends T>)visitor).visitBlock(this);
			else return visitor.visitChildren(this);
		}
	}

	public final BlockContext block() throws RecognitionException {
		BlockContext _localctx = new BlockContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_block);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(28);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (((_la) & ~0x3f) == 0 && ((1L << _la) & 35332816896L) != 0) {
				{
				{
				setState(25);
				stat();
				}
				}
				setState(30);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
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
		public AssignmentContext assignment() {
			return getRuleContext(AssignmentContext.class,0);
		}
		public If_statContext if_stat() {
			return getRuleContext(If_statContext.class,0);
		}
		public While_statContext while_stat() {
			return getRuleContext(While_statContext.class,0);
		}
		public LogContext log() {
			return getRuleContext(LogContext.class,0);
		}
		public TerminalNode OTHER() { return getToken(MUParser.OTHER, 0); }
		public StatContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_stat; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).enterStat(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).exitStat(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor) return ((MUVisitor<? extends T>)visitor).visitStat(this);
			else return visitor.visitChildren(this);
		}
	}

	public final StatContext stat() throws RecognitionException {
		StatContext _localctx = new StatContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_stat);
		try {
			setState(37);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case ID:
				enterOuterAlt(_localctx, 1);
				{
				setState(31);
				assignment();
				}
				break;
			case IF:
				enterOuterAlt(_localctx, 2);
				{
				setState(32);
				if_stat();
				}
				break;
			case WHILE:
				enterOuterAlt(_localctx, 3);
				{
				setState(33);
				while_stat();
				}
				break;
			case LOG:
				enterOuterAlt(_localctx, 4);
				{
				setState(34);
				log();
				}
				break;
			case OTHER:
				enterOuterAlt(_localctx, 5);
				{
				setState(35);
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
	public static class AssignmentContext extends ParserRuleContext {
		public TerminalNode ID() { return getToken(MUParser.ID, 0); }
		public TerminalNode ASSIGN() { return getToken(MUParser.ASSIGN, 0); }
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public TerminalNode SCOL() { return getToken(MUParser.SCOL, 0); }
		public AssignmentContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_assignment; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).enterAssignment(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).exitAssignment(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor) return ((MUVisitor<? extends T>)visitor).visitAssignment(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AssignmentContext assignment() throws RecognitionException {
		AssignmentContext _localctx = new AssignmentContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_assignment);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(39);
			match(ID);
			setState(40);
			match(ASSIGN);
			setState(41);
			expr(0);
			setState(42);
			match(SCOL);
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
		public List<TerminalNode> IF() { return getTokens(MUParser.IF); }
		public TerminalNode IF(int i) {
			return getToken(MUParser.IF, i);
		}
		public List<Condition_blockContext> condition_block() {
			return getRuleContexts(Condition_blockContext.class);
		}
		public Condition_blockContext condition_block(int i) {
			return getRuleContext(Condition_blockContext.class,i);
		}
		public List<TerminalNode> ELSE() { return getTokens(MUParser.ELSE); }
		public TerminalNode ELSE(int i) {
			return getToken(MUParser.ELSE, i);
		}
		public Stat_blockContext stat_block() {
			return getRuleContext(Stat_blockContext.class,0);
		}
		public If_statContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_if_stat; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).enterIf_stat(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).exitIf_stat(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor) return ((MUVisitor<? extends T>)visitor).visitIf_stat(this);
			else return visitor.visitChildren(this);
		}
	}

	public final If_statContext if_stat() throws RecognitionException {
		If_statContext _localctx = new If_statContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_if_stat);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(44);
			match(IF);
			setState(45);
			condition_block();
			setState(51);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,2,_ctx);
			while ( _alt!=2 && _alt!= ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(46);
					match(ELSE);
					setState(47);
					match(IF);
					setState(48);
					condition_block();
					}
					}
				}
				setState(53);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,2,_ctx);
			}
			setState(56);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,3,_ctx) ) {
			case 1:
				{
				setState(54);
				match(ELSE);
				setState(55);
				stat_block();
				}
				break;
			}
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
		public Stat_blockContext stat_block() {
			return getRuleContext(Stat_blockContext.class,0);
		}
		public Condition_blockContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_condition_block; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).enterCondition_block(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).exitCondition_block(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor) return ((MUVisitor<? extends T>)visitor).visitCondition_block(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Condition_blockContext condition_block() throws RecognitionException {
		Condition_blockContext _localctx = new Condition_blockContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_condition_block);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(58);
			expr(0);
			setState(59);
			stat_block();
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
		public TerminalNode OBRACE() { return getToken(MUParser.OBRACE, 0); }
		public BlockContext block() {
			return getRuleContext(BlockContext.class,0);
		}
		public TerminalNode CBRACE() { return getToken(MUParser.CBRACE, 0); }
		public StatContext stat() {
			return getRuleContext(StatContext.class,0);
		}
		public Stat_blockContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_stat_block; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).enterStat_block(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).exitStat_block(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor) return ((MUVisitor<? extends T>)visitor).visitStat_block(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Stat_blockContext stat_block() throws RecognitionException {
		Stat_blockContext _localctx = new Stat_blockContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_stat_block);
		try {
			setState(66);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case OBRACE:
				enterOuterAlt(_localctx, 1);
				{
				setState(61);
				match(OBRACE);
				setState(62);
				block();
				setState(63);
				match(CBRACE);
				}
				break;
			case IF:
			case WHILE:
			case LOG:
			case ID:
			case OTHER:
				enterOuterAlt(_localctx, 2);
				{
				setState(65);
				stat();
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
	public static class While_statContext extends ParserRuleContext {
		public TerminalNode WHILE() { return getToken(MUParser.WHILE, 0); }
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public Stat_blockContext stat_block() {
			return getRuleContext(Stat_blockContext.class,0);
		}
		public While_statContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_while_stat; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).enterWhile_stat(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).exitWhile_stat(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor) return ((MUVisitor<? extends T>)visitor).visitWhile_stat(this);
			else return visitor.visitChildren(this);
		}
	}

	public final While_statContext while_stat() throws RecognitionException {
		While_statContext _localctx = new While_statContext(_ctx, getState());
		enterRule(_localctx, 14, RULE_while_stat);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(68);
			match(WHILE);
			setState(69);
			expr(0);
			setState(70);
			stat_block();
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
	public static class LogContext extends ParserRuleContext {
		public TerminalNode LOG() { return getToken(MUParser.LOG, 0); }
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public TerminalNode SCOL() { return getToken(MUParser.SCOL, 0); }
		public LogContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_log; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).enterLog(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).exitLog(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor) return ((MUVisitor<? extends T>)visitor).visitLog(this);
			else return visitor.visitChildren(this);
		}
	}

	public final LogContext log() throws RecognitionException {
		LogContext _localctx = new LogContext(_ctx, getState());
		enterRule(_localctx, 16, RULE_log);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(72);
			match(LOG);
			setState(73);
			expr(0);
			setState(74);
			match(SCOL);
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
		public TerminalNode NOT() { return getToken(MUParser.NOT, 0); }
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public NotExprContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).enterNotExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).exitNotExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor) return ((MUVisitor<? extends T>)visitor).visitNotExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class UnaryMinusExprContext extends ExprContext {
		public TerminalNode MINUS() { return getToken(MUParser.MINUS, 0); }
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public UnaryMinusExprContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).enterUnaryMinusExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).exitUnaryMinusExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor) return ((MUVisitor<? extends T>)visitor).visitUnaryMinusExpr(this);
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
		public TerminalNode MULT() { return getToken(MUParser.MULT, 0); }
		public TerminalNode DIV() { return getToken(MUParser.DIV, 0); }
		public TerminalNode MOD() { return getToken(MUParser.MOD, 0); }
		public MultiplicationExprContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).enterMultiplicationExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).exitMultiplicationExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor) return ((MUVisitor<? extends T>)visitor).visitMultiplicationExpr(this);
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
			if ( listener instanceof MUListener) ((MUListener)listener).enterAtomExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).exitAtomExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor) return ((MUVisitor<? extends T>)visitor).visitAtomExpr(this);
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
		public TerminalNode OR() { return getToken(MUParser.OR, 0); }
		public OrExprContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).enterOrExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).exitOrExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor) return ((MUVisitor<? extends T>)visitor).visitOrExpr(this);
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
		public TerminalNode PLUS() { return getToken(MUParser.PLUS, 0); }
		public TerminalNode MINUS() { return getToken(MUParser.MINUS, 0); }
		public AdditiveExprContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).enterAdditiveExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).exitAdditiveExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor) return ((MUVisitor<? extends T>)visitor).visitAdditiveExpr(this);
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
		public TerminalNode POW() { return getToken(MUParser.POW, 0); }
		public PowExprContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).enterPowExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).exitPowExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor) return ((MUVisitor<? extends T>)visitor).visitPowExpr(this);
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
		public TerminalNode LTEQ() { return getToken(MUParser.LTEQ, 0); }
		public TerminalNode GTEQ() { return getToken(MUParser.GTEQ, 0); }
		public TerminalNode LT() { return getToken(MUParser.LT, 0); }
		public TerminalNode GT() { return getToken(MUParser.GT, 0); }
		public RelationalExprContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).enterRelationalExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).exitRelationalExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor) return ((MUVisitor<? extends T>)visitor).visitRelationalExpr(this);
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
		public TerminalNode EQ() { return getToken(MUParser.EQ, 0); }
		public TerminalNode NEQ() { return getToken(MUParser.NEQ, 0); }
		public EqualityExprContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).enterEqualityExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).exitEqualityExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor) return ((MUVisitor<? extends T>)visitor).visitEqualityExpr(this);
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
		public TerminalNode AND() { return getToken(MUParser.AND, 0); }
		public AndExprContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).enterAndExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).exitAndExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor) return ((MUVisitor<? extends T>)visitor).visitAndExpr(this);
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
		int _startState = 18;
		enterRecursionRule(_localctx, 18, RULE_expr, _p);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(82);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case MINUS:
				{
				_localctx = new UnaryMinusExprContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;

				setState(77);
				match(MINUS);
				setState(78);
				expr(9);
				}
				break;
			case NOT:
				{
				_localctx = new NotExprContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(79);
				match(NOT);
				setState(80);
				expr(8);
				}
				break;
			case OPAR:
			case TRUE:
			case FALSE:
			case NIL:
			case ID:
			case INT:
			case FLOAT:
			case STRING:
				{
				_localctx = new AtomExprContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(81);
				atom();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			_ctx.stop = _input.LT(-1);
			setState(107);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,7,_ctx);
			while ( _alt!=2 && _alt!= ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					setState(105);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,6,_ctx) ) {
					case 1:
						{
						_localctx = new PowExprContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(84);
						if (!(precpred(_ctx, 10))) throw new FailedPredicateException(this, "precpred(_ctx, 10)");
						setState(85);
						match(POW);
						setState(86);
						expr(11);
						}
						break;
					case 2:
						{
						_localctx = new MultiplicationExprContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(87);
						if (!(precpred(_ctx, 7))) throw new FailedPredicateException(this, "precpred(_ctx, 7)");
						setState(88);
						((MultiplicationExprContext)_localctx).op = _input.LT(1);
						_la = _input.LA(1);
						if ( !(((_la) & ~0x3f) == 0 && ((1L << _la) & 14336L) != 0) ) {
							((MultiplicationExprContext)_localctx).op = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(89);
						expr(8);
						}
						break;
					case 3:
						{
						_localctx = new AdditiveExprContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(90);
						if (!(precpred(_ctx, 6))) throw new FailedPredicateException(this, "precpred(_ctx, 6)");
						setState(91);
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
						setState(92);
						expr(7);
						}
						break;
					case 4:
						{
						_localctx = new RelationalExprContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(93);
						if (!(precpred(_ctx, 5))) throw new FailedPredicateException(this, "precpred(_ctx, 5)");
						setState(94);
						((RelationalExprContext)_localctx).op = _input.LT(1);
						_la = _input.LA(1);
						if ( !(((_la) & ~0x3f) == 0 && ((1L << _la) & 480L) != 0) ) {
							((RelationalExprContext)_localctx).op = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(95);
						expr(6);
						}
						break;
					case 5:
						{
						_localctx = new EqualityExprContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(96);
						if (!(precpred(_ctx, 4))) throw new FailedPredicateException(this, "precpred(_ctx, 4)");
						setState(97);
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
						setState(98);
						expr(5);
						}
						break;
					case 6:
						{
						_localctx = new AndExprContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(99);
						if (!(precpred(_ctx, 3))) throw new FailedPredicateException(this, "precpred(_ctx, 3)");
						setState(100);
						match(AND);
						setState(101);
						expr(4);
						}
						break;
					case 7:
						{
						_localctx = new OrExprContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(102);
						if (!(precpred(_ctx, 2))) throw new FailedPredicateException(this, "precpred(_ctx, 2)");
						setState(103);
						match(OR);
						setState(104);
						expr(3);
						}
						break;
					}
					}
				}
				setState(109);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,7,_ctx);
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
		public TerminalNode OPAR() { return getToken(MUParser.OPAR, 0); }
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public TerminalNode CPAR() { return getToken(MUParser.CPAR, 0); }
		public ParExprContext(AtomContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).enterParExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).exitParExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor) return ((MUVisitor<? extends T>)visitor).visitParExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class BooleanAtomContext extends AtomContext {
		public TerminalNode TRUE() { return getToken(MUParser.TRUE, 0); }
		public TerminalNode FALSE() { return getToken(MUParser.FALSE, 0); }
		public BooleanAtomContext(AtomContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).enterBooleanAtom(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).exitBooleanAtom(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor) return ((MUVisitor<? extends T>)visitor).visitBooleanAtom(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class IdAtomContext extends AtomContext {
		public TerminalNode ID() { return getToken(MUParser.ID, 0); }
		public IdAtomContext(AtomContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).enterIdAtom(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).exitIdAtom(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor) return ((MUVisitor<? extends T>)visitor).visitIdAtom(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class StringAtomContext extends AtomContext {
		public TerminalNode STRING() { return getToken(MUParser.STRING, 0); }
		public StringAtomContext(AtomContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).enterStringAtom(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).exitStringAtom(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor) return ((MUVisitor<? extends T>)visitor).visitStringAtom(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class NilAtomContext extends AtomContext {
		public TerminalNode NIL() { return getToken(MUParser.NIL, 0); }
		public NilAtomContext(AtomContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).enterNilAtom(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).exitNilAtom(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor) return ((MUVisitor<? extends T>)visitor).visitNilAtom(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class NumberAtomContext extends AtomContext {
		public TerminalNode INT() { return getToken(MUParser.INT, 0); }
		public TerminalNode FLOAT() { return getToken(MUParser.FLOAT, 0); }
		public NumberAtomContext(AtomContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).enterNumberAtom(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener) ((MUListener)listener).exitNumberAtom(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor) return ((MUVisitor<? extends T>)visitor).visitNumberAtom(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AtomContext atom() throws RecognitionException {
		AtomContext _localctx = new AtomContext(_ctx, getState());
		enterRule(_localctx, 20, RULE_atom);
		int _la;
		try {
			setState(119);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case OPAR:
				_localctx = new ParExprContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(110);
				match(OPAR);
				setState(111);
				expr(0);
				setState(112);
				match(CPAR);
				}
				break;
			case INT:
			case FLOAT:
				_localctx = new NumberAtomContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(114);
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
				setState(115);
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
				setState(116);
				match(ID);
				}
				break;
			case STRING:
				_localctx = new StringAtomContext(_localctx);
				enterOuterAlt(_localctx, 5);
				{
				setState(117);
				match(STRING);
				}
				break;
			case NIL:
				_localctx = new NilAtomContext(_localctx);
				enterOuterAlt(_localctx, 6);
				{
				setState(118);
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
		case 9:
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
		"\u0004\u0001#z\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002\u0002"+
		"\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004\u0002\u0005"+
		"\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007\u0007\u0007\u0002\b\u0007"+
		"\b\u0002\t\u0007\t\u0002\n\u0007\n\u0001\u0000\u0001\u0000\u0001\u0000"+
		"\u0001\u0001\u0005\u0001\u001b\b\u0001\n\u0001\f\u0001\u001e\t\u0001\u0001"+
		"\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0003"+
		"\u0002&\b\u0002\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001"+
		"\u0003\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0005"+
		"\u00042\b\u0004\n\u0004\f\u00045\t\u0004\u0001\u0004\u0001\u0004\u0003"+
		"\u00049\b\u0004\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0006\u0001"+
		"\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0003\u0006C\b\u0006\u0001"+
		"\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001\b\u0001\b\u0001\b\u0001"+
		"\b\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0003\tS\b\t\u0001"+
		"\t\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0001"+
		"\t\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0001"+
		"\t\u0001\t\u0001\t\u0005\tj\b\t\n\t\f\tm\t\t\u0001\n\u0001\n\u0001\n\u0001"+
		"\n\u0001\n\u0001\n\u0001\n\u0001\n\u0001\n\u0003\nx\b\n\u0001\n\u0000"+
		"\u0001\u0012\u000b\u0000\u0002\u0004\u0006\b\n\f\u000e\u0010\u0012\u0014"+
		"\u0000\u0006\u0001\u0000\u000b\r\u0001\u0000\t\n\u0001\u0000\u0005\b\u0001"+
		"\u0000\u0003\u0004\u0001\u0000\u001e\u001f\u0001\u0000\u0016\u0017\u0084"+
		"\u0000\u0016\u0001\u0000\u0000\u0000\u0002\u001c\u0001\u0000\u0000\u0000"+
		"\u0004%\u0001\u0000\u0000\u0000\u0006\'\u0001\u0000\u0000\u0000\b,\u0001"+
		"\u0000\u0000\u0000\n:\u0001\u0000\u0000\u0000\fB\u0001\u0000\u0000\u0000"+
		"\u000eD\u0001\u0000\u0000\u0000\u0010H\u0001\u0000\u0000\u0000\u0012R"+
		"\u0001\u0000\u0000\u0000\u0014w\u0001\u0000\u0000\u0000\u0016\u0017\u0003"+
		"\u0002\u0001\u0000\u0017\u0018\u0005\u0000\u0000\u0001\u0018\u0001\u0001"+
		"\u0000\u0000\u0000\u0019\u001b\u0003\u0004\u0002\u0000\u001a\u0019\u0001"+
		"\u0000\u0000\u0000\u001b\u001e\u0001\u0000\u0000\u0000\u001c\u001a\u0001"+
		"\u0000\u0000\u0000\u001c\u001d\u0001\u0000\u0000\u0000\u001d\u0003\u0001"+
		"\u0000\u0000\u0000\u001e\u001c\u0001\u0000\u0000\u0000\u001f&\u0003\u0006"+
		"\u0003\u0000 &\u0003\b\u0004\u0000!&\u0003\u000e\u0007\u0000\"&\u0003"+
		"\u0010\b\u0000#$\u0005#\u0000\u0000$&\u0006\u0002\uffff\uffff\u0000%\u001f"+
		"\u0001\u0000\u0000\u0000% \u0001\u0000\u0000\u0000%!\u0001\u0000\u0000"+
		"\u0000%\"\u0001\u0000\u0000\u0000%#\u0001\u0000\u0000\u0000&\u0005\u0001"+
		"\u0000\u0000\u0000\'(\u0005\u001d\u0000\u0000()\u0005\u0011\u0000\u0000"+
		")*\u0003\u0012\t\u0000*+\u0005\u0010\u0000\u0000+\u0007\u0001\u0000\u0000"+
		"\u0000,-\u0005\u0019\u0000\u0000-3\u0003\n\u0005\u0000./\u0005\u001a\u0000"+
		"\u0000/0\u0005\u0019\u0000\u000002\u0003\n\u0005\u00001.\u0001\u0000\u0000"+
		"\u000025\u0001\u0000\u0000\u000031\u0001\u0000\u0000\u000034\u0001\u0000"+
		"\u0000\u000048\u0001\u0000\u0000\u000053\u0001\u0000\u0000\u000067\u0005"+
		"\u001a\u0000\u000079\u0003\f\u0006\u000086\u0001\u0000\u0000\u000089\u0001"+
		"\u0000\u0000\u00009\t\u0001\u0000\u0000\u0000:;\u0003\u0012\t\u0000;<"+
		"\u0003\f\u0006\u0000<\u000b\u0001\u0000\u0000\u0000=>\u0005\u0014\u0000"+
		"\u0000>?\u0003\u0002\u0001\u0000?@\u0005\u0015\u0000\u0000@C\u0001\u0000"+
		"\u0000\u0000AC\u0003\u0004\u0002\u0000B=\u0001\u0000\u0000\u0000BA\u0001"+
		"\u0000\u0000\u0000C\r\u0001\u0000\u0000\u0000DE\u0005\u001b\u0000\u0000"+
		"EF\u0003\u0012\t\u0000FG\u0003\f\u0006\u0000G\u000f\u0001\u0000\u0000"+
		"\u0000HI\u0005\u001c\u0000\u0000IJ\u0003\u0012\t\u0000JK\u0005\u0010\u0000"+
		"\u0000K\u0011\u0001\u0000\u0000\u0000LM\u0006\t\uffff\uffff\u0000MN\u0005"+
		"\n\u0000\u0000NS\u0003\u0012\t\tOP\u0005\u000f\u0000\u0000PS\u0003\u0012"+
		"\t\bQS\u0003\u0014\n\u0000RL\u0001\u0000\u0000\u0000RO\u0001\u0000\u0000"+
		"\u0000RQ\u0001\u0000\u0000\u0000Sk\u0001\u0000\u0000\u0000TU\n\n\u0000"+
		"\u0000UV\u0005\u000e\u0000\u0000Vj\u0003\u0012\t\u000bWX\n\u0007\u0000"+
		"\u0000XY\u0007\u0000\u0000\u0000Yj\u0003\u0012\t\bZ[\n\u0006\u0000\u0000"+
		"[\\\u0007\u0001\u0000\u0000\\j\u0003\u0012\t\u0007]^\n\u0005\u0000\u0000"+
		"^_\u0007\u0002\u0000\u0000_j\u0003\u0012\t\u0006`a\n\u0004\u0000\u0000"+
		"ab\u0007\u0003\u0000\u0000bj\u0003\u0012\t\u0005cd\n\u0003\u0000\u0000"+
		"de\u0005\u0002\u0000\u0000ej\u0003\u0012\t\u0004fg\n\u0002\u0000\u0000"+
		"gh\u0005\u0001\u0000\u0000hj\u0003\u0012\t\u0003iT\u0001\u0000\u0000\u0000"+
		"iW\u0001\u0000\u0000\u0000iZ\u0001\u0000\u0000\u0000i]\u0001\u0000\u0000"+
		"\u0000i`\u0001\u0000\u0000\u0000ic\u0001\u0000\u0000\u0000if\u0001\u0000"+
		"\u0000\u0000jm\u0001\u0000\u0000\u0000ki\u0001\u0000\u0000\u0000kl\u0001"+
		"\u0000\u0000\u0000l\u0013\u0001\u0000\u0000\u0000mk\u0001\u0000\u0000"+
		"\u0000no\u0005\u0012\u0000\u0000op\u0003\u0012\t\u0000pq\u0005\u0013\u0000"+
		"\u0000qx\u0001\u0000\u0000\u0000rx\u0007\u0004\u0000\u0000sx\u0007\u0005"+
		"\u0000\u0000tx\u0005\u001d\u0000\u0000ux\u0005 \u0000\u0000vx\u0005\u0018"+
		"\u0000\u0000wn\u0001\u0000\u0000\u0000wr\u0001\u0000\u0000\u0000ws\u0001"+
		"\u0000\u0000\u0000wt\u0001\u0000\u0000\u0000wu\u0001\u0000\u0000\u0000"+
		"wv\u0001\u0000\u0000\u0000x\u0015\u0001\u0000\u0000\u0000\t\u001c%38B"+
		"Rikw";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}