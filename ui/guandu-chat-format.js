/*
 * 观读轻译 1.4.1 的 content/chat-format.js 原样移植：Markdown 先在本地解析成 token，
 * 再用白名单 DOM 节点构建，不执行模型输出里的 HTML、脚本或远程图片。
 */
(() => {
  'use strict';
  if(globalThis.GuanduChatFormat)return;
  const parser=globalThis.markdownit({html:false,linkify:true,breaks:true,typographer:false,maxNesting:32});
  parser.use(globalThis.markdownitEmoji,{shortcuts:{}});
  const emojiPattern=/\p{Extended_Pictographic}|\p{Regional_Indicator}|\u20e3/u;
  const graphemes=typeof Intl.Segmenter==='function'?new Intl.Segmenter(undefined,{granularity:'grapheme'}):null;

  // Models sometimes produce **标题： **, which CommonMark treats as literal stars.
  // Repair only whitespace-adjacent double markers, leaving escapes and code intact.
  function repairInline(source){
    let result='',i=0;
    function codeEnd(start){let n=1;while(source[start+n]==='`')n++;const marker='`'.repeat(n),end=source.indexOf(marker,start+n);return end<0?start+n:end+n;}
    while(i<source.length){
      if(source[i]==='\\'&&i+1<source.length){result+=source.slice(i,i+2);i+=2;continue;}
      if(source[i]==='`'){const end=codeEnd(i);result+=source.slice(i,end);i=end;continue;}
      const marker=source.slice(i,i+2);
      if((marker==='**'||marker==='__')&&source[i-1]!==marker[0]&&source[i+2]!==marker[0]){
        let cursor=i+2,end=-1;
        while(cursor<source.length){
          if(source[cursor]==='\\'){cursor+=2;continue;}
          if(source[cursor]==='`'){cursor=codeEnd(cursor);continue;}
          if(source.slice(cursor,cursor+2)===marker&&source[cursor-1]!==marker[0]&&source[cursor+2]!==marker[0]){end=cursor;break;}
          cursor++;
        }
        if(end>=0){const inside=source.slice(i+2,end);if(inside.trim()&&!inside.includes('\n\n')){result+=marker+(inside!==inside.trim()?inside.trim():inside)+marker;i=end+2;continue;}}
      }
      result+=source[i++];
    }
    return result;
  }
  function parse(text){
    const tokens=parser.parse(String(text||''),{});
    // Work on inline content only. Fenced and indented code remain byte-for-byte text.
    for(const token of tokens)if(token.type==='inline'){
      const repaired=repairInline(token.content);
      if(repaired!==token.content){const fresh=parser.parseInline(repaired,{});token.children=fresh[0]?.children||token.children;}
    }
    return tokens;
  }
  function safeLink(value,base){
    if(typeof value!=='string'||value.length>4096||/[\u0000-\u001f\u007f]/.test(value))return null;
    try{const url=new URL(value,base);return ['http:','https:','mailto:'].includes(url.protocol)?url.href:null;}catch{return null;}
  }
  function appendText(parent,text,doc){
    let pending='';
    const flush=()=>{if(pending){parent.append(doc.createTextNode(pending));pending='';}};
    const segments=graphemes?graphemes.segment(text):Array.from(text);
    for(const part of segments){const segment=typeof part==='string'?part:part.segment;if(emojiPattern.test(segment)){flush();const span=doc.createElement('span');span.className='gd-chat-emoji';span.textContent=segment;parent.append(span);}else pending+=segment;}
    flush();
  }
  const tags=new Set(['p','h1','h2','h3','h4','h5','h6','ul','ol','li','blockquote','strong','em','s','a','table','thead','tbody','tr','th','td']);
  function build(tokens,parent,doc){
    const stack=[parent];
    for(const token of tokens){
      const at=stack[stack.length-1];
      if(token.hidden)continue;
      if(token.type==='inline'){build(token.children||[],at,doc);continue;}
      if(token.type==='text'||token.type==='emoji'||token.type==='html_inline'||token.type==='html_block'){appendText(at,token.content||'',doc);continue;}
      if(token.type==='softbreak'||token.type==='hardbreak'){at.append(doc.createElement('br'));continue;}
      if(token.type==='code_inline'){const code=doc.createElement('code');code.textContent=token.content;at.append(code);continue;}
      if(token.type==='fence'||token.type==='code_block'){
        const box=doc.createElement('div');box.className='gd-chat-codeblock';
        const header=doc.createElement('div');header.className='gd-chat-code-header';
        const language=doc.createElement('span');language.textContent=/^[\w+#.-]{1,32}$/.test(token.info?.trim().split(/\s+/)[0]||'')?token.info.trim().split(/\s+/)[0]:'代码';
        const copy=doc.createElement('button');copy.type='button';copy.className='gd-chat-code-copy';copy.textContent='复制代码';
        copy.addEventListener('click',async()=>{try{await navigator.clipboard.writeText(token.content);copy.textContent='已复制';}catch{copy.textContent='请手动复制';}});
        header.append(language,copy);const pre=doc.createElement('pre'),code=doc.createElement('code');code.textContent=token.content;pre.append(code);box.append(header,pre);at.append(box);continue;
      }
      if(token.type==='hr'){at.append(doc.createElement('hr'));continue;}
      if(token.type==='image'){
        const href=safeLink(token.attrGet('src'),doc.baseURI),link=doc.createElement(href?'a':'span');link.textContent='🖼 '+(token.content||'图片');
        if(href){link.href=href;link.target='_blank';link.rel='noopener noreferrer';link.referrerPolicy='no-referrer';}at.append(link);continue;
      }
      if(token.nesting===1&&tags.has(token.tag)){
        let node=doc.createElement(token.tag);
        if(token.tag==='a'){
          const href=safeLink(token.attrGet('href'),doc.baseURI);
          if(href){node.href=href;node.target='_blank';node.rel='noopener noreferrer';node.referrerPolicy='no-referrer';const title=token.attrGet('title');if(title)node.title=title.slice(0,500);}else node=doc.createElement('span');
        }
        if(token.tag==='ol'){const start=Number(token.attrGet('start'));if(Number.isInteger(start)&&start>0&&start<1000000)node.start=start;}
        if(token.tag==='td'||token.tag==='th'){const align=/^text-align:(left|right|center)$/.exec(token.attrGet('style')||'');if(align)node.style.textAlign=align[1];}
        if(token.tag==='table'){const wrap=doc.createElement('div');wrap.className='gd-chat-table-wrap';wrap.append(node);at.append(wrap);}else at.append(node);
        stack.push(node);continue;
      }
      if(token.nesting===-1&&tags.has(token.tag)){if(stack.length>1)stack.pop();continue;}
      if(token.content)appendText(at,token.content,doc);
    }
  }
  function render(target,text){
    const doc=target.ownerDocument,fragment=doc.createDocumentFragment();
    try{build(parse(text),fragment,doc);target.replaceChildren(fragment);target.classList.add('gd-chat-rich');}
    catch{target.textContent=String(text||'');target.classList.remove('gd-chat-rich');}
  }
  Object.defineProperty(globalThis,'GuanduChatFormat',{value:Object.freeze({parse,render,repairInline,safeLink})});
})();
