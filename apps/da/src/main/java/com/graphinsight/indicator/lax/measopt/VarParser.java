// Generated from java-escape by ANTLR 4.11.1
package com.graphinsight.indicator.lax.measopt;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue"})
public class VarParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.11.1", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, T__1=2, T__2=3, VAR=4, NUM=5, MEASTEXT=6, ADD=7, SUB=8, MUL=9, 
		MULX=10, DIV=11, DIVX=12, NEWLINE=13, SPACE=14;
	public static final int
		RULE_gra = 0, RULE_stat = 1, RULE_expr = 2;
	private static String[] makeRuleNames() {
		return new String[] {
			"gra", "stat", "expr"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'='", "'('", "')'", null, null, null, "'+'", "'-'", "'*'", "'\\u00D7'", 
			"'/'", "'\\u00F7'", null, "' '"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, null, null, null, "VAR", "NUM", "MEASTEXT", "ADD", "SUB", "MUL", 
			"MULX", "DIV", "DIVX", "NEWLINE", "SPACE"
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

	public VarParser(TokenStream input) {
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
			if ( listener instanceof VarListener ) ((VarListener)listener).enterGra(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof VarListener ) ((VarListener)listener).exitGra(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof VarVisitor ) return ((VarVisitor<? extends T>)visitor).visitGra(this);
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
			setState(7); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(6);
				stat();
				}
				}
				setState(9); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( ((_la) & ~0x3f) == 0 && ((1L << _la) & 8308L) != 0 );
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
		public TerminalNode NEWLINE() { return getToken(VarParser.NEWLINE, 0); }
		public BlankContext(StatContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof VarListener ) ((VarListener)listener).enterBlank(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof VarListener ) ((VarListener)listener).exitBlank(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof VarVisitor ) return ((VarVisitor<? extends T>)visitor).visitBlank(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ValueContext extends StatContext {
		public TerminalNode VAR() { return getToken(VarParser.VAR, 0); }
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public TerminalNode NEWLINE() { return getToken(VarParser.NEWLINE, 0); }
		public ValueContext(StatContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof VarListener ) ((VarListener)listener).enterValue(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof VarListener ) ((VarListener)listener).exitValue(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof VarVisitor ) return ((VarVisitor<? extends T>)visitor).visitValue(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class CalContext extends StatContext {
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public TerminalNode NEWLINE() { return getToken(VarParser.NEWLINE, 0); }
		public CalContext(StatContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof VarListener ) ((VarListener)listener).enterCal(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof VarListener ) ((VarListener)listener).exitCal(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof VarVisitor ) return ((VarVisitor<? extends T>)visitor).visitCal(this);
			else return visitor.visitChildren(this);
		}
	}

	public final StatContext stat() throws RecognitionException {
		StatContext _localctx = new StatContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_stat);
		try {
			setState(20);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,1,_ctx) ) {
			case 1:
				_localctx = new CalContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(11);
				expr(0);
				setState(12);
				match(NEWLINE);
				}
				break;
			case 2:
				_localctx = new ValueContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(14);
				match(VAR);
				setState(15);
				match(T__0);
				setState(16);
				expr(0);
				setState(17);
				match(NEWLINE);
				}
				break;
			case 3:
				_localctx = new BlankContext(_localctx);
				enterOuterAlt(_localctx, 3);
				{
				setState(19);
				match(NEWLINE);
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
	public static class AdditionAndSubtractionContext extends ExprContext {
		public Token operator;
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public TerminalNode ADD() { return getToken(VarParser.ADD, 0); }
		public TerminalNode SUB() { return getToken(VarParser.SUB, 0); }
		public AdditionAndSubtractionContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof VarListener ) ((VarListener)listener).enterAdditionAndSubtraction(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof VarListener ) ((VarListener)listener).exitAdditionAndSubtraction(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof VarVisitor ) return ((VarVisitor<? extends T>)visitor).visitAdditionAndSubtraction(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class MultiplyAndDivideContext extends ExprContext {
		public Token operator;
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public TerminalNode MUL() { return getToken(VarParser.MUL, 0); }
		public TerminalNode DIV() { return getToken(VarParser.DIV, 0); }
		public TerminalNode MULX() { return getToken(VarParser.MULX, 0); }
		public TerminalNode DIVX() { return getToken(VarParser.DIVX, 0); }
		public MultiplyAndDivideContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof VarListener ) ((VarListener)listener).enterMultiplyAndDivide(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof VarListener ) ((VarListener)listener).exitMultiplyAndDivide(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof VarVisitor ) return ((VarVisitor<? extends T>)visitor).visitMultiplyAndDivide(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class VarContext extends ExprContext {
		public TerminalNode VAR() { return getToken(VarParser.VAR, 0); }
		public VarContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof VarListener ) ((VarListener)listener).enterVar(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof VarListener ) ((VarListener)listener).exitVar(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof VarVisitor ) return ((VarVisitor<? extends T>)visitor).visitVar(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class NumContext extends ExprContext {
		public TerminalNode NUM() { return getToken(VarParser.NUM, 0); }
		public NumContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof VarListener ) ((VarListener)listener).enterNum(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof VarListener ) ((VarListener)listener).exitNum(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof VarVisitor ) return ((VarVisitor<? extends T>)visitor).visitNum(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class MeasContext extends ExprContext {
		public TerminalNode MEASTEXT() { return getToken(VarParser.MEASTEXT, 0); }
		public MeasContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof VarListener ) ((VarListener)listener).enterMeas(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof VarListener ) ((VarListener)listener).exitMeas(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof VarVisitor ) return ((VarVisitor<? extends T>)visitor).visitMeas(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class BracketsContext extends ExprContext {
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public BracketsContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof VarListener ) ((VarListener)listener).enterBrackets(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof VarListener ) ((VarListener)listener).exitBrackets(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof VarVisitor ) return ((VarVisitor<? extends T>)visitor).visitBrackets(this);
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
		int _startState = 4;
		enterRecursionRule(_localctx, 4, RULE_expr, _p);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(30);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case NUM:
				{
				_localctx = new NumContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;

				setState(23);
				match(NUM);
				}
				break;
			case VAR:
				{
				_localctx = new VarContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(24);
				match(VAR);
				}
				break;
			case MEASTEXT:
				{
				_localctx = new MeasContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(25);
				match(MEASTEXT);
				}
				break;
			case T__1:
				{
				_localctx = new BracketsContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(26);
				match(T__1);
				setState(27);
				expr(0);
				setState(28);
				match(T__2);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			_ctx.stop = _input.LT(-1);
			setState(40);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,4,_ctx);
			while ( _alt!=2 && _alt!= ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					setState(38);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,3,_ctx) ) {
					case 1:
						{
						_localctx = new MultiplyAndDivideContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(32);
						if (!(precpred(_ctx, 6))) throw new FailedPredicateException(this, "precpred(_ctx, 6)");
						setState(33);
						((MultiplyAndDivideContext)_localctx).operator = _input.LT(1);
						_la = _input.LA(1);
						if ( !(((_la) & ~0x3f) == 0 && ((1L << _la) & 7680L) != 0) ) {
							((MultiplyAndDivideContext)_localctx).operator = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(34);
						expr(7);
						}
						break;
					case 2:
						{
						_localctx = new AdditionAndSubtractionContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(35);
						if (!(precpred(_ctx, 5))) throw new FailedPredicateException(this, "precpred(_ctx, 5)");
						setState(36);
						((AdditionAndSubtractionContext)_localctx).operator = _input.LT(1);
						_la = _input.LA(1);
						if ( !(_la==ADD || _la==SUB) ) {
							((AdditionAndSubtractionContext)_localctx).operator = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(37);
						expr(6);
						}
						break;
					}
					} 
				}
				setState(42);
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

	public boolean sempred(RuleContext _localctx, int ruleIndex, int predIndex) {
		switch (ruleIndex) {
		case 2:
			return expr_sempred((ExprContext)_localctx, predIndex);
		}
		return true;
	}
	private boolean expr_sempred(ExprContext _localctx, int predIndex) {
		switch (predIndex) {
		case 0:
			return precpred(_ctx, 6);
		case 1:
			return precpred(_ctx, 5);
		}
		return true;
	}

	public static final String _serializedATN =
		"\u0004\u0001\u000e,\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002"+
		"\u0002\u0007\u0002\u0001\u0000\u0004\u0000\b\b\u0000\u000b\u0000\f\u0000"+
		"\t\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001"+
		"\u0001\u0001\u0001\u0001\u0001\u0001\u0003\u0001\u0015\b\u0001\u0001\u0002"+
		"\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002"+
		"\u0001\u0002\u0003\u0002\u001f\b\u0002\u0001\u0002\u0001\u0002\u0001\u0002"+
		"\u0001\u0002\u0001\u0002\u0001\u0002\u0005\u0002\'\b\u0002\n\u0002\f\u0002"+
		"*\t\u0002\u0001\u0002\u0000\u0001\u0004\u0003\u0000\u0002\u0004\u0000"+
		"\u0002\u0001\u0000\t\f\u0001\u0000\u0007\b0\u0000\u0007\u0001\u0000\u0000"+
		"\u0000\u0002\u0014\u0001\u0000\u0000\u0000\u0004\u001e\u0001\u0000\u0000"+
		"\u0000\u0006\b\u0003\u0002\u0001\u0000\u0007\u0006\u0001\u0000\u0000\u0000"+
		"\b\t\u0001\u0000\u0000\u0000\t\u0007\u0001\u0000\u0000\u0000\t\n\u0001"+
		"\u0000\u0000\u0000\n\u0001\u0001\u0000\u0000\u0000\u000b\f\u0003\u0004"+
		"\u0002\u0000\f\r\u0005\r\u0000\u0000\r\u0015\u0001\u0000\u0000\u0000\u000e"+
		"\u000f\u0005\u0004\u0000\u0000\u000f\u0010\u0005\u0001\u0000\u0000\u0010"+
		"\u0011\u0003\u0004\u0002\u0000\u0011\u0012\u0005\r\u0000\u0000\u0012\u0015"+
		"\u0001\u0000\u0000\u0000\u0013\u0015\u0005\r\u0000\u0000\u0014\u000b\u0001"+
		"\u0000\u0000\u0000\u0014\u000e\u0001\u0000\u0000\u0000\u0014\u0013\u0001"+
		"\u0000\u0000\u0000\u0015\u0003\u0001\u0000\u0000\u0000\u0016\u0017\u0006"+
		"\u0002\uffff\uffff\u0000\u0017\u001f\u0005\u0005\u0000\u0000\u0018\u001f"+
		"\u0005\u0004\u0000\u0000\u0019\u001f\u0005\u0006\u0000\u0000\u001a\u001b"+
		"\u0005\u0002\u0000\u0000\u001b\u001c\u0003\u0004\u0002\u0000\u001c\u001d"+
		"\u0005\u0003\u0000\u0000\u001d\u001f\u0001\u0000\u0000\u0000\u001e\u0016"+
		"\u0001\u0000\u0000\u0000\u001e\u0018\u0001\u0000\u0000\u0000\u001e\u0019"+
		"\u0001\u0000\u0000\u0000\u001e\u001a\u0001\u0000\u0000\u0000\u001f(\u0001"+
		"\u0000\u0000\u0000 !\n\u0006\u0000\u0000!\"\u0007\u0000\u0000\u0000\""+
		"\'\u0003\u0004\u0002\u0007#$\n\u0005\u0000\u0000$%\u0007\u0001\u0000\u0000"+
		"%\'\u0003\u0004\u0002\u0006& \u0001\u0000\u0000\u0000&#\u0001\u0000\u0000"+
		"\u0000\'*\u0001\u0000\u0000\u0000(&\u0001\u0000\u0000\u0000()\u0001\u0000"+
		"\u0000\u0000)\u0005\u0001\u0000\u0000\u0000*(\u0001\u0000\u0000\u0000"+
		"\u0005\t\u0014\u001e&(";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}