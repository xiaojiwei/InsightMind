// Generated from java-escape by ANTLR 4.11.1
package com.graphinsight.indicator.xlax.xlod;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue"})
public class LodExprParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.11.1", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, T__1=2, T__2=3, T__3=4, T__4=5, T__5=6, T__6=7, T__7=8, T__8=9, 
		FILTER=10, MEA=11, DIM=12, MUL=13, DIV=14, ADD=15, SUB=16, ID=17, INT=18, 
		STRING=19, NEWLINE=20, WS=21;
	public static final int
		RULE_prog = 0, RULE_stat = 1, RULE_expr = 2, RULE_meas = 3, RULE_dims = 4, 
		RULE_filters = 5;
	private static String[] makeRuleNames() {
		return new String[] {
			"prog", "stat", "expr", "meas", "dims", "filters"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'='", "'('", "')'", "'{'", "'>='", "'<='", "'in'", "'between'", 
			"'}'", null, null, null, "'*'", "'/'", "'+'", "'-'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, null, null, null, null, null, null, null, null, null, "FILTER", 
			"MEA", "DIM", "MUL", "DIV", "ADD", "SUB", "ID", "INT", "STRING", "NEWLINE", 
			"WS"
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

	public LodExprParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ProgContext extends ParserRuleContext {
		public List<StatContext> stat() {
			return getRuleContexts(StatContext.class);
		}
		public StatContext stat(int i) {
			return getRuleContext(StatContext.class,i);
		}
		public ProgContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_prog; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).enterProg(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).exitProg(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LodExprVisitor ) return ((LodExprVisitor<? extends T>)visitor).visitProg(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ProgContext prog() throws RecognitionException {
		ProgContext _localctx = new ProgContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_prog);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(13); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(12);
				stat();
				}
				}
				setState(15); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( ((_la) & ~0x3f) == 0 && ((1L << _la) & 1181700L) != 0 );
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
		public StatContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_stat; }
	 
		public StatContext() { }
		public void copyFrom(StatContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class BlankContext extends StatContext {
		public TerminalNode NEWLINE() { return getToken(LodExprParser.NEWLINE, 0); }
		public BlankContext(StatContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).enterBlank(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).exitBlank(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LodExprVisitor ) return ((LodExprVisitor<? extends T>)visitor).visitBlank(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class PrintExprContext extends StatContext {
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public TerminalNode NEWLINE() { return getToken(LodExprParser.NEWLINE, 0); }
		public PrintExprContext(StatContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).enterPrintExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).exitPrintExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LodExprVisitor ) return ((LodExprVisitor<? extends T>)visitor).visitPrintExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class AssignContext extends StatContext {
		public TerminalNode ID() { return getToken(LodExprParser.ID, 0); }
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public TerminalNode NEWLINE() { return getToken(LodExprParser.NEWLINE, 0); }
		public AssignContext(StatContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).enterAssign(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).exitAssign(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LodExprVisitor ) return ((LodExprVisitor<? extends T>)visitor).visitAssign(this);
			else return visitor.visitChildren(this);
		}
	}

	public final StatContext stat() throws RecognitionException {
		StatContext _localctx = new StatContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_stat);
		try {
			setState(26);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__1:
			case MEA:
				_localctx = new PrintExprContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(17);
				expr();
				setState(18);
				match(NEWLINE);
				}
				break;
			case ID:
				_localctx = new AssignContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(20);
				match(ID);
				setState(21);
				match(T__0);
				setState(22);
				expr();
				setState(23);
				match(NEWLINE);
				}
				break;
			case NEWLINE:
				_localctx = new BlankContext(_localctx);
				enterOuterAlt(_localctx, 3);
				{
				setState(25);
				match(NEWLINE);
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
	public static class ParensContext extends ExprContext {
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public ParensContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).enterParens(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).exitParens(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LodExprVisitor ) return ((LodExprVisitor<? extends T>)visitor).visitParens(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class MulDivContext extends ExprContext {
		public Token op;
		public List<MeasContext> meas() {
			return getRuleContexts(MeasContext.class);
		}
		public MeasContext meas(int i) {
			return getRuleContext(MeasContext.class,i);
		}
		public TerminalNode MUL() { return getToken(LodExprParser.MUL, 0); }
		public TerminalNode DIV() { return getToken(LodExprParser.DIV, 0); }
		public MulDivContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).enterMulDiv(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).exitMulDiv(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LodExprVisitor ) return ((LodExprVisitor<? extends T>)visitor).visitMulDiv(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class AddSubContext extends ExprContext {
		public Token op;
		public List<MeasContext> meas() {
			return getRuleContexts(MeasContext.class);
		}
		public MeasContext meas(int i) {
			return getRuleContext(MeasContext.class,i);
		}
		public TerminalNode ADD() { return getToken(LodExprParser.ADD, 0); }
		public TerminalNode SUB() { return getToken(LodExprParser.SUB, 0); }
		public AddSubContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).enterAddSub(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).exitAddSub(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LodExprVisitor ) return ((LodExprVisitor<? extends T>)visitor).visitAddSub(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ExprContext expr() throws RecognitionException {
		ExprContext _localctx = new ExprContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_expr);
		int _la;
		try {
			setState(40);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,2,_ctx) ) {
			case 1:
				_localctx = new MulDivContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(28);
				meas();
				setState(29);
				((MulDivContext)_localctx).op = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==MUL || _la==DIV) ) {
					((MulDivContext)_localctx).op = (Token)_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(30);
				meas();
				}
				break;
			case 2:
				_localctx = new AddSubContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(32);
				meas();
				setState(33);
				((AddSubContext)_localctx).op = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==ADD || _la==SUB) ) {
					((AddSubContext)_localctx).op = (Token)_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(34);
				meas();
				}
				break;
			case 3:
				_localctx = new ParensContext(_localctx);
				enterOuterAlt(_localctx, 3);
				{
				setState(36);
				match(T__1);
				setState(37);
				expr();
				setState(38);
				match(T__2);
				}
				break;
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
	public static class MeasContext extends ParserRuleContext {
		public MeasContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_meas; }
	 
		public MeasContext() { }
		public void copyFrom(MeasContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class MeaContext extends MeasContext {
		public TerminalNode MEA() { return getToken(LodExprParser.MEA, 0); }
		public List<DimsContext> dims() {
			return getRuleContexts(DimsContext.class);
		}
		public DimsContext dims(int i) {
			return getRuleContext(DimsContext.class,i);
		}
		public List<FiltersContext> filters() {
			return getRuleContexts(FiltersContext.class);
		}
		public FiltersContext filters(int i) {
			return getRuleContext(FiltersContext.class,i);
		}
		public MeaContext(MeasContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).enterMea(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).exitMea(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LodExprVisitor ) return ((LodExprVisitor<? extends T>)visitor).visitMea(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MeasContext meas() throws RecognitionException {
		MeasContext _localctx = new MeasContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_meas);
		int _la;
		try {
			_localctx = new MeaContext(_localctx);
			enterOuterAlt(_localctx, 1);
			{
			setState(42);
			match(MEA);
			{
			setState(44); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(43);
				dims();
				}
				}
				setState(46); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( _la==DIM );
			}
			{
			setState(49); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(48);
				filters();
				}
				}
				setState(51); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( _la==T__3 );
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
	public static class DimsContext extends ParserRuleContext {
		public DimsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_dims; }
	 
		public DimsContext() { }
		public void copyFrom(DimsContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class DimContext extends DimsContext {
		public TerminalNode DIM() { return getToken(LodExprParser.DIM, 0); }
		public DimContext(DimsContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).enterDim(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).exitDim(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LodExprVisitor ) return ((LodExprVisitor<? extends T>)visitor).visitDim(this);
			else return visitor.visitChildren(this);
		}
	}

	public final DimsContext dims() throws RecognitionException {
		DimsContext _localctx = new DimsContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_dims);
		try {
			_localctx = new DimContext(_localctx);
			enterOuterAlt(_localctx, 1);
			{
			setState(53);
			match(DIM);
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
	public static class FiltersContext extends ParserRuleContext {
		public FiltersContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_filters; }
	 
		public FiltersContext() { }
		public void copyFrom(FiltersContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class FilterContext extends FiltersContext {
		public Token ops;
		public DimsContext dims() {
			return getRuleContext(DimsContext.class,0);
		}
		public TerminalNode STRING() { return getToken(LodExprParser.STRING, 0); }
		public FilterContext(FiltersContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).enterFilter(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).exitFilter(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LodExprVisitor ) return ((LodExprVisitor<? extends T>)visitor).visitFilter(this);
			else return visitor.visitChildren(this);
		}
	}

	public final FiltersContext filters() throws RecognitionException {
		FiltersContext _localctx = new FiltersContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_filters);
		int _la;
		try {
			_localctx = new FilterContext(_localctx);
			enterOuterAlt(_localctx, 1);
			{
			setState(55);
			match(T__3);
			setState(56);
			dims();
			setState(57);
			((FilterContext)_localctx).ops = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((_la) & ~0x3f) == 0 && ((1L << _la) & 480L) != 0) ) {
				((FilterContext)_localctx).ops = (Token)_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(58);
			match(STRING);
			setState(59);
			match(T__8);
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

	public static final String _serializedATN =
		"\u0004\u0001\u0015>\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002"+
		"\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004\u0002"+
		"\u0005\u0007\u0005\u0001\u0000\u0004\u0000\u000e\b\u0000\u000b\u0000\f"+
		"\u0000\u000f\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001"+
		"\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0003\u0001\u001b\b\u0001"+
		"\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002"+
		"\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002"+
		"\u0003\u0002)\b\u0002\u0001\u0003\u0001\u0003\u0004\u0003-\b\u0003\u000b"+
		"\u0003\f\u0003.\u0001\u0003\u0004\u00032\b\u0003\u000b\u0003\f\u00033"+
		"\u0001\u0004\u0001\u0004\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005"+
		"\u0001\u0005\u0001\u0005\u0001\u0005\u0000\u0000\u0006\u0000\u0002\u0004"+
		"\u0006\b\n\u0000\u0003\u0001\u0000\r\u000e\u0001\u0000\u000f\u0010\u0001"+
		"\u0000\u0005\b>\u0000\r\u0001\u0000\u0000\u0000\u0002\u001a\u0001\u0000"+
		"\u0000\u0000\u0004(\u0001\u0000\u0000\u0000\u0006*\u0001\u0000\u0000\u0000"+
		"\b5\u0001\u0000\u0000\u0000\n7\u0001\u0000\u0000\u0000\f\u000e\u0003\u0002"+
		"\u0001\u0000\r\f\u0001\u0000\u0000\u0000\u000e\u000f\u0001\u0000\u0000"+
		"\u0000\u000f\r\u0001\u0000\u0000\u0000\u000f\u0010\u0001\u0000\u0000\u0000"+
		"\u0010\u0001\u0001\u0000\u0000\u0000\u0011\u0012\u0003\u0004\u0002\u0000"+
		"\u0012\u0013\u0005\u0014\u0000\u0000\u0013\u001b\u0001\u0000\u0000\u0000"+
		"\u0014\u0015\u0005\u0011\u0000\u0000\u0015\u0016\u0005\u0001\u0000\u0000"+
		"\u0016\u0017\u0003\u0004\u0002\u0000\u0017\u0018\u0005\u0014\u0000\u0000"+
		"\u0018\u001b\u0001\u0000\u0000\u0000\u0019\u001b\u0005\u0014\u0000\u0000"+
		"\u001a\u0011\u0001\u0000\u0000\u0000\u001a\u0014\u0001\u0000\u0000\u0000"+
		"\u001a\u0019\u0001\u0000\u0000\u0000\u001b\u0003\u0001\u0000\u0000\u0000"+
		"\u001c\u001d\u0003\u0006\u0003\u0000\u001d\u001e\u0007\u0000\u0000\u0000"+
		"\u001e\u001f\u0003\u0006\u0003\u0000\u001f)\u0001\u0000\u0000\u0000 !"+
		"\u0003\u0006\u0003\u0000!\"\u0007\u0001\u0000\u0000\"#\u0003\u0006\u0003"+
		"\u0000#)\u0001\u0000\u0000\u0000$%\u0005\u0002\u0000\u0000%&\u0003\u0004"+
		"\u0002\u0000&\'\u0005\u0003\u0000\u0000\')\u0001\u0000\u0000\u0000(\u001c"+
		"\u0001\u0000\u0000\u0000( \u0001\u0000\u0000\u0000($\u0001\u0000\u0000"+
		"\u0000)\u0005\u0001\u0000\u0000\u0000*,\u0005\u000b\u0000\u0000+-\u0003"+
		"\b\u0004\u0000,+\u0001\u0000\u0000\u0000-.\u0001\u0000\u0000\u0000.,\u0001"+
		"\u0000\u0000\u0000./\u0001\u0000\u0000\u0000/1\u0001\u0000\u0000\u0000"+
		"02\u0003\n\u0005\u000010\u0001\u0000\u0000\u000023\u0001\u0000\u0000\u0000"+
		"31\u0001\u0000\u0000\u000034\u0001\u0000\u0000\u00004\u0007\u0001\u0000"+
		"\u0000\u000056\u0005\f\u0000\u00006\t\u0001\u0000\u0000\u000078\u0005"+
		"\u0004\u0000\u000089\u0003\b\u0004\u00009:\u0007\u0002\u0000\u0000:;\u0005"+
		"\u0013\u0000\u0000;<\u0005\t\u0000\u0000<\u000b\u0001\u0000\u0000\u0000"+
		"\u0005\u000f\u001a(.3";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}