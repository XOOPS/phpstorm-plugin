<{foreach item=story from=$story}>
  <h2><{$story.title}></h2>
  <{foreach item=story from=$story.related}><{$story.title}><{/foreach}>
  <{* <{/foreach}> *}>
  <{$story|default:''}> <{$storyline}>
<{foreachelse}>
  <{$story}>
<{/foreach}>
<{foreach item=row from=$rows}><{$row}><{/foreach}>
<{foreach from=$items|default:null key=k item=items}><{$items}><{/foreach}>
<{foreach item=x from=$x.list}><{$x}><{/foreach}>
