Web前端二面模拟面试题（大厂对标版）

📋 面试说明

项目	内容

面试岗位	Web前端开发工程师

面试轮次	二面（技术深度面）

对标企业	腾讯、阿里巴巴、字节跳动等一线大厂

面试时长	60-90分钟

考察重点	原理深入理解、性能优化、复杂场景处理、项目经验、架构设计能力

评分标准	每题满分5分，总分100分，70分及格，85分以上优秀

一、React 深入（共5题，25分）

第1题：React的render函数返回的数据类型是什么？什么时候会触发render？(5分)

考察意图： 考察对React渲染机制的理解



问题： React的render函数返回的数据类型是什么？在什么情况下会触发render？



答案：



返回的数据类型：

React的render函数返回的其实是一种描述UI结构的"蓝图"，不是真实的DOM。这个返回值本质上是React元素（React Element），也就是一个普通的JavaScript对象。



例如，写<div className="app">Hello</div>，经过JSX编译后实际返回的是React.createElement('div', {className: 'app'}, 'Hello')，最终得到一个对象：



javascript

{

&nbsp; type: 'div',

&nbsp; props: {

&nbsp;   className: 'app',

&nbsp;   children: 'Hello'

&nbsp; }

}

这个对象会被React内部用来和上次渲染的结果做对比，找出变化的部分，再更新到真实DOM上，这个过程叫协调（Reconciliation）。所以每次render都不直接操作DOM，而是生成一个新的描述对象。



除了对象，render还可以返回null、字符串、数字、数组（包含以上类型）、或布尔值（用于条件渲染，false/null不渲染）。但不能返回Promise、函数、或undefined。



render触发的时机：



组件自身状态变化：调用setState时，React会把更新放进任务队列，后续进入渲染阶段就会触发render。但前提是新状态和上一次不一样，或者你强制更新了。



父组件重新渲染：父组件重新渲染，子组件默认也会跟着render。哪怕没传新props，只要父级render了，子组件就跑不掉。想避免就得用React.memo做浅比较，或者配合useMemo控制依赖。



路由跳转或Context变化：只要组件消费了这些变化，比如用了useRouter或useContext，数据源一更新，用到的地方都会响应。



强制更新：类组件可以调用forceUpdate()，函数组件可以用useReducer或自定义hook实现类似效果。



注意：



useState初始化函数只执行一次，后续render不会再算



如果setState的值和之前完全相等，函数组件会跳过render，类组件的shouldComponentUpdate返回false也能拦住



评分标准：



5分：准确说明返回类型，完整列举触发时机，有深入理解



3-4分：基本说明返回类型，触发时机列举不全



1-2分：概念模糊，说不清楚



0分：完全不知道



第2题：React的setState什么时候是同步的，什么时候是异步的？(5分)

考察意图： 考察对React状态更新机制的理解



问题： React中setState什么时候是同步的，什么时候是异步的？请详细说明。



答案：



setState的同步异步行为，得看它是不是在React的合成事件和生命周期方法里调用：



1\. 在React管控的上下文里（异步）

比如onClick、onInput这些合成事件，或者componentDidMount、useEffect里，setState是异步的。React会把多个setState合并成一次更新，提升性能。你连续调两次setState，state不会立刻变，this.state拿到的还是旧值。



2\. 跑到原生DOM事件或者setTimeout里（同步）

比如用addEventListener绑定click，setState执行完state马上就更新。因为这时候React不再拦截和批量处理这些调用。



3\. 自动批处理（automatic batching）- React 18+

从React 18开始，ReactDOM.render被ReactDOM.createRoot替代后，不仅合成事件，连Promise、setTimeout、原生事件里的setState默认也会被批处理。也就是说，在新版本里，大部分场景下setState表现都趋向于异步合并。



4\. 强制同步更新

如果你手动用flushSync包一层，那就会强制同步执行：



javascript

flushSync(() => {

&nbsp; setState(42)

})

// 到这行时，DOM已经更新

结论：



老版本看是否在合成事件里



新版本默认全包批处理，除非你主动用flushSync控制节奏



评分标准：



5分：准确说明同步异步条件，能区分不同版本行为



3-4分：基本说明区别，但版本差异不清晰



1-2分：概念模糊，说不清楚



0分：完全不知道



第3题：React的事件机制是怎样的？合成事件是什么？(5分)

考察意图： 考察对React事件系统的深入理解



问题： React的事件机制是怎样的？什么是合成事件（SyntheticEvent）？和原生DOM事件有什么区别？



答案：



React的事件处理是基于合成事件机制实现的，它并不是直接绑定在DOM元素上，而是通过事件委托统一挂载到document上。



合成事件的工作原理：



事件委托：React把所有事件都绑定在document上，采用事件委托的方式统一管理。比如你给一个button写onClick，React不会真把这个函数挂到button上，而是记录下来，等事件冒泡到document时再统一派发。这样一来，页面上成百上千个按钮也不会有性能问题，毕竟事件监听器不经过每个节点。



合成事件对象：触发时，React会创建一个SyntheticEvent实例，它是对原生事件的封装，抹平了不同浏览器之间的差异。这个对象在事件回调中传入，用完就被池化回收，提升性能。所以你不能在异步代码里访问event.target，因为属性可能已经被清空了。



事件生命周期：分三步：收集阶段、合成阶段、派发阶段。先从触发元素往上收集所有React组件的事件处理函数，然后创建合成事件，最后按React自己的冒泡规则执行。



和原生DOM事件的区别：



命名方式不同：React用驼峰式onClick，原生HTML是小写onclick



阻止默认行为的方式不同：不能像原生那样靠return false，必须显式调用e.preventDefault()



事件对象是池化的：React为了性能，会复用事件对象，意味着异步场景下比如setTimeout里访问e，它的属性可能已经清空了。要用就得提前缓存



跨浏览器兼容：合成事件抹平了浏览器差异，不用担心IE的老问题



注意： 合成事件在旧版React中会复用事件对象（设为null），所以在异步代码中不能访问e。这一点在React 17+已调整，事件不再池化，可以异步读取。



评分标准：



5分：完整说明合成事件机制，清晰指出和原生事件的区别



3-4分：基本说明机制，但区别不够清晰



1-2分：概念模糊，只知道委托机制



0分：完全不知道



第4题：React中key的作用是什么？为什么不能用index？(5分)

考察意图： 考察对React diff算法的理解



问题： React中key的作用是什么？为什么不能用index作为key？什么场景下可以用？



答案：



key的作用：

React在渲染列表时，需要通过某种方式识别每个元素的唯一性，从而决定是更新、移动还是销毁DOM节点。key就是用来帮助React高效比对虚拟DOM树中列表节点的标识。



没有key的情况下，React默认采用"就地复用"策略，按索引位置对比新旧节点。一旦列表顺序变化，比如在头部插入一项，后面所有项虽然内容没变，但索引都变了，导致全部重新渲染，性能很差。



设置合理的key，比如用数据的唯一ID（user.id、订单号等），能让React精准识别每个节点的身份。即使顺序调整，React也能知道哪个节点被移动、新增或删除，只做最小化更新。



为什么不能用index作为key？



用数组索引index当key是常见误区。初始渲染没问题，但涉及增删、排序时，index会变，导致组件状态错乱或DOM更新异常。



具体问题演示：



javascript

// 错误做法

{items.map((item, index) => <div key={index}>{item.name}</div>)}



// 正确做法

{items.map(item => <div key={item.id}>{item.name}</div>)}

比如列表项带输入框，往上插入一条，下面的输入内容全跟着上移了。因为React认为索引变了，组件实例没有复用，状态就丢了。



什么场景下可以用index？

静态列表、纯展示且无增删改场景下，用index问题不大。比如一个永远不会变化的配置展示列表，用index做key是可以接受的。



理想key的要求：

稳定、可预测的唯一值，来自数据本身。数据库主键、UUID都行。



评分标准：



5分：准确说明key作用，清晰解释不能用index的原因，指出可用场景



3-4分：基本说明作用，原因解释不够深入



1-2分：知道不能用index，但说不清原因



0分：完全不知道



第5题：React.memo、useMemo、useCallback的作用和区别？(5分)

考察意图： 考察对React性能优化的理解



问题： React.memo、useMemo、useCallback分别有什么作用？它们的区别是什么？



答案：



这三个API都是React提供的性能优化手段，用于避免不必要的重新渲染和计算，但作用对象和使用场景不同：



1\. React.memo（高阶组件）



作用：对组件进行浅比较，如果props没有变化，则跳过该组件的重新渲染



适用：纯展示组件，props变化不频繁的场景



注意：如果组件内部使用了useState、useContext等，即使props没变，这些内部状态变化仍会触发渲染



javascript

const ChildComponent = React.memo(function Child({ data }) {

&nbsp; return <div>{data}</div>;

});

2\. useMemo（缓存计算结果）



作用：缓存计算值，只有依赖项变化时才重新计算



适用：复杂计算、大数据处理、避免每次渲染都重新创建对象/数组



注意：第一个参数是函数，返回需要缓存的值



javascript

const memoizedValue = useMemo(() => {

&nbsp; return expensiveComputation(a, b);

}, \[a, b]);

3\. useCallback（缓存函数引用）



作用：缓存函数引用，只有依赖项变化时才返回新函数



适用：将函数作为props传给子组件时，避免子组件因函数引用变化而重复渲染



注意：useCallback(fn, deps)等价于useMemo(() => fn, deps)



javascript

const memoizedCallback = useCallback(() => {

&nbsp; doSomething(a, b);

}, \[a, b]);

区别总结：



API	缓存对象	使用场景	返回值

React.memo	组件	避免组件重复渲染	一个新的组件

useMemo	计算值	避免重复计算	计算后的值

useCallback	函数	避免函数重新创建	缓存的函数

最佳实践：



不要过度优化，先看性能瓶颈在哪



useMemo和useCallback的依赖数组要正确设置



配合React.memo使用效果更好



评分标准：



5分：准确说明三者作用和区别，有代码示例，指出最佳实践



3-4分：基本说明作用，但区别不够清晰



1-2分：概念混淆，说不清楚



0分：完全不知道



二、Vue 深入（共5题，25分）

第6题：Vue的响应式原理是什么？Vue 2和Vue 3有什么区别？(5分)

考察意图： 考察对Vue响应式系统的深入理解



问题： Vue的响应式原理是什么？Vue 2和Vue 3的响应式实现有什么区别？



答案：



Vue 2的响应式原理：

Vue 2的响应式系统基于Object.defineProperty，它在初始化时就劫持了对象所有属性的getter和setter。



依赖收集：当组件渲染时，会访问data中的属性，触发getter，此时收集当前组件的Watcher作为依赖



派发更新：当属性变化时，触发setter，通知所有依赖该属性的Watcher重新计算，从而更新视图



Vue 2的局限性：



无法检测对象属性的添加和删除（因为初始化时才劫持）



无法直接通过下标修改数组（需要特殊处理）



需要递归遍历对象所有属性，初始化开销大



javascript

// Vue 2中新增属性需要特殊处理

this.$set(this.obj, 'newProp', 'value')

Vue 3的响应式原理：

Vue 3改用Proxy实现响应式，可以代理整个对象，而不是对象的属性。



优势：可以检测到对象属性的添加、删除、数组索引修改等所有操作



懒响应：只有在访问嵌套对象时才递归代理，初始化性能更好



兼容性：不支持IE11及以下浏览器



javascript

// Vue 3中直接新增属性即可响应

const state = reactive({ obj: {} })

state.obj.newProp = 'value' // 自动响应式

为什么Vue 2给对象添加新属性后界面不刷新？

因为Vue 2的Object.defineProperty在初始化时就劫持了对象所有属性的getter和setter。后来添加的新属性，根本没有被定义过get/set，自然不会触发依赖收集和派发更新。



解决方案（Vue 2）：



提前声明好属性，哪怕初始值是undefined



用Vue.set(obj, 'newProp', 'hi')或this.$set(obj, 'newProp', 'hi')



评分标准：



5分：准确说明两种实现原理，清晰指出区别和局限性



3-4分：基本说明原理，但区别不够清晰



1-2分：知道有区别，说不清原理



0分：完全不知道



第7题：Vue的nextTick有什么作用？原理是什么？(5分)

考察意图： 考察对Vue异步更新队列的理解



问题： Vue的nextTick有什么作用？它的实现原理是什么？



答案：



nextTick的作用：

Vue的nextTick利用的是浏览器的异步任务机制，把回调函数推迟到DOM更新周期结束后再执行。



每次修改响应式数据，Vue不会立刻更新DOM，而是把这些变更缓存起来，等到当前事件循环末尾统一处理。这时候如果你需要在DOM更新后做些操作，比如获取更新后的元素尺寸，就得靠nextTick。



javascript

this.$nextTick(() => {

&nbsp; // 这里可以安全访问更新后的DOM

&nbsp; console.log(this.$refs.container.scrollHeight)

})



// 或者用async/await

await this.$nextTick()

console.log('DOM已更新')

nextTick的原理：



它背后的核心是微任务优先策略。Vue会优先尝试用Promise.then、MutationObserver或setImmediate，降级到setTimeout。所以你调nextTick，其实是把回调塞进了微任务队列，确保它在所有同步的DOM变更完成后、下一个宏任务开始前执行。



执行流程：



数据变化后，Vue不会立即更新DOM，而是开启一个队列



在同一事件循环中发生的所有数据变更，会被去重后推入这个队列



在下一个事件循环开始前，Vue刷新队列并执行实际DOM更新



nextTick的回调会在DOM更新后执行



常见使用场景：



手动触发$refs计算布局



在动态插入列表后获取滚动高度



在数据变化后操作DOM元素



注意：



数据变更后需操作真实DOM的场景必须用



它不解决跨组件通信，也不是定时器替代品



理解它的本质是微任务调度，不是Vue自己实现的DOM监听



评分标准：



5分：准确说明作用，清晰解释原理，有代码示例



3-4分：基本说明作用，原理解释不够深入



1-2分：知道用法，说不清原理



0分：完全不知道



第8题：为什么不建议在Vue中同时使用v-if和v-for？(5分)

考察意图： 考察对Vue指令优先级和性能优化的理解



问题： 为什么不建议在Vue中同时使用v-if和v-for？正确的做法是什么？



答案：



原因分析：



Vue在处理指令时会按照优先级顺序解析，v-for的优先级高于v-if。这意味着即使你把两个指令写在同一个元素上，Vue也会先执行v-for进行列表渲染，然后再对每一项单独判断v-if的条件。



这就带来两个明显问题：



1\. 性能浪费

v-if是基于条件控制显示或隐藏，但如果它和v-for一起用，即便某项数据不符合v-if条件、最终不会显示，它依然会被遍历、创建虚拟DOM、执行判断逻辑。比如有100条数据，只有10条满足条件，那剩下的90项不经过渲染，但它们的循环开销是实打实存在的。



2\. 代码可读性差

这种写法容易让人误解为"只遍历符合条件的项"，但实际上它是"遍历所有项再逐个过滤"，语义不清晰，后期维护容易出错。



html

<!-- 不好的写法 -->

<li v-for="item in items" v-if="item.isActive" :key="item.id">

&nbsp; {{ item.name }}

</li>

正确做法：



方案1：把v-if写在外层包裹元素上

控制整个列表区域的显隐



html

<ul v-if="items.length">

&nbsp; <li v-for="item in items" :key="item.id">{{ item.name }}</li>

</ul>

<p v-else>暂无数据</p>

方案2：在computed中预先过滤数据源

让v-for只处理精简后的数组



javascript

computed: {

&nbsp; activeItems() {

&nbsp;   return this.items.filter(item => !item.isInactive)

&nbsp; }

}

html

<li v-for="item in activeItems" :key="item.id">{{ item.name }}</li>

方案3：用template包装



html

<template v-for="item in items" :key="item.id">

&nbsp; <li v-if="item.isActive">{{ item.name }}</li>

</template>

评分标准：



5分：准确说明原因，清晰指出优先级问题，给出多种正确做法



3-4分：基本说明原因，但解决方案不够全面



1-2分：知道不能用，但说不清原因



0分：完全不知道



第9题：Vue的组件通信方式有哪些？(5分)

考察意图： 考察对Vue组件通信的全面理解



问题： Vue中父子组件之间传值有哪些方式？跨层级组件如何通信？



答案：



Vue父子组件传值有多种方式，根据场景选择合适的方式：



1\. props / $emit（父子通信）



父传子：通过props向下传数据



javascript

// 父组件

<user-card :name="userName" :age="userAge" />



// 子组件

props: \['name', 'age']

子传父：通过$emit触发事件



javascript

// 子组件

this.$emit('update-name', newName)



// 父组件

<user-card @update-name="handleNameChange" />

2\. $refs / $parent / $children（直接访问）



父调子：通过ref调用子组件方法或访问属性



javascript

// 父组件

<child-component ref="childComp" />

this.$refs.childComp.someMethod()

子调父：通过$parent访问父组件实例（不推荐，耦合度高）



3\. provide / inject（跨层级通信）

祖先组件用provide提供数据，后代组件用inject直接获取，跨多层都行。但注意这不是响应式的，除非你传的是个响应式对象。



javascript

// 祖先组件

provide() {

&nbsp; return {

&nbsp;   theme: this.theme,

&nbsp;   user: this.user

&nbsp; }

}



// 后代组件

inject: \['theme', 'user']

4\. EventBus（全局事件总线）

适用于非父子组件通信，但Vue 3中已不推荐，可用mitt等第三方库替代。



javascript

// 创建事件总线

Vue.prototype.$bus = new Vue()



// 发送事件

this.$bus.$emit('event-name', data)



// 接收事件

this.$bus.$on('event-name', (data) => {})

5\. Vuex / Pinia（全局状态管理）

适用于复杂应用、多组件共享状态



javascript

// Vuex

this.$store.commit('mutation', data)

this.$store.dispatch('action', data)



// Pinia（Vue 3推荐）

const store = useStore()

store.updateData(data)

选型建议：



简单场景优先props + emit



深度嵌套考虑provide/inject



特殊操作再用ref



复杂状态管理用Pinia/Vuex



评分标准：



5分：完整列举所有通信方式，说明适用场景



3-4分：列举主要方式，但场景说明不够清晰



1-2分：只知道props/emit，其他不了解



0分：完全不知道



第10题：Vue Router的hash模式和history模式有什么区别？(5分)

考察意图： 考察对前端路由原理的理解



问题： Vue Router的hash模式和history模式有什么区别？各自的优缺点和适用场景是什么？



答案：



Vue Router的路由模式选择，本质上是前端路由如何映射URL和页面状态的问题。两种模式在用户体验和实现机制上有明显差异。



1\. hash模式



URL形式：依赖URL中的#后面的部分，比如example.com/#/home



原理：#后的内容变化不会触发浏览器向服务器发起请求，所以前端可以自由控制路由跳转，后端不参与



兼容性：好，IE8也能用



服务器配置：不需要特殊配置



适用场景：老项目、对部署要求低的场景、静态站点托管（如GitHub Pages）



2\. history模式



URL形式：看起来就是标准路径，像example.com/home



原理：利用HTML5的History API（pushState、replaceState）修改URL而不刷新页面



兼容性：IE10及以上



服务器配置：需要后端配合，所有路由都得指向同一个入口文件（如index.html），否则用户直接访问这个路径会404



Nginx配置示例：



nginx

try\_files $uri $uri/ /index.html;

3\. 代码切换



javascript

const router = new VueRouter({

&nbsp; mode: 'history', // 或 'hash'

&nbsp; routes

})

优缺点对比：



特性	hash模式	history模式

URL美观	有#，不美观	干净美观

服务器配置	不需要	需要配置回退

兼容性	IE8+	IE10+

刷新行为	不会404	可能404

部署难度	低	略高

总结：



hash模式的#是锚点标识，本来用于页面内定位



history模式利用HTML5 History API，体验更好，但部署成本略高



如果服务端搞不定fallback路由，就别强上history模式，不然静态资源可能都加载不了



评分标准：



5分：准确说明两种模式区别，清晰指出优缺点和适用场景



3-4分：基本说明区别，但优缺点不够清晰



1-2分：知道有区别，说不清原理



0分：完全不知道



三、性能优化（共4题，20分）

第11题：前端性能优化有哪些手段？(5分)

考察意图： 考察对前端性能优化的综合理解



问题： 请列举前端性能优化的常见手段，至少说出8种。



答案：



前端性能优化可以从多个维度入手，以下是常见的优化手段：



1\. 资源加载优化



图片优化：使用WebP格式、响应式图片（srcset）、图片懒加载



代码拆分：按需加载（Code Splitting），减少首屏JS体积



Tree Shaking：移除未使用的代码



压缩混淆：压缩HTML、CSS、JS文件



2\. 网络优化



CDN加速：将静态资源部署到CDN，减少网络延迟



HTTP/2：开启HTTP/2，多路复用减少连接数



预加载：<link rel="preload">提前加载关键资源



预连接：<link rel="preconnect">提前建立连接



3\. 缓存策略



强缓存：设置Cache-Control和Expires



协商缓存：使用ETag和Last-Modified



Service Worker缓存：实现离线访问



4\. 渲染优化



减少重排重绘：避免频繁操作DOM，使用class批量修改样式



虚拟滚动：只渲染可视区域的列表项



懒执行：延迟非关键任务的执行



5\. 代码优化



防抖节流：控制事件处理函数的执行频率



函数式组件：减少组件开销



避免不必要的渲染：React使用memo、useMemo、useCallback；Vue使用computed、v-once



6\. 首屏加载优化



SSR/静态生成：Next.js、Nuxt.js等服务端渲染



骨架屏：在数据加载前显示占位内容



关键CSS内联：将首屏关键CSS内联到HTML中



7\. 监控与度量



性能指标监控：LCP、FID、CLS等核心Web指标



错误监控：Sentry等错误监控工具



8\. 构建优化



按需引入：如lodash按需加载，antd按需引入样式



模块联邦：微前端架构下的共享依赖



评分标准：



5分：完整列举8种以上优化手段，分类清晰



3-4分：列举5-7种优化手段，分类不够清晰



1-2分：列举3-4种，理解较浅



0分：几乎不知道



第12题：当大模型API响应延迟超过1秒时，前端可以采取哪些优化策略保证用户体验？(5分)

考察意图： 考察对用户体验优化的理解



问题： 当大模型API响应延迟超过1秒时，前端可以采取哪些优化策略保证用户体验？



答案：



前端能做的其实很有限，核心是"让用户感觉快"，而不是真的让模型变快。大模型API本身耗时主要在服务端推理，前端更多是体验层的兜底。



1\. 立即反馈（骨架屏/加载状态）

请求发出去之后，立刻给用户反馈，别干等。比如马上展示一个"思考中..."的动画或者骨架屏，告诉用户系统已经在处理了，避免误操作重复提交。



jsx

{loading ? <Skeleton active /> : <Result data={data} />}

2\. 预期管理

如果接口超过800ms还没回来，可以主动提示"生成时间较长，请稍候"。这种预期管理很重要，用户知道要等，就不会觉得卡死了。



3\. 流式响应（Streaming）

优先考虑流式响应。让后端以SSE或WebSocket方式分段返回结果，前端拿到一段就渲染一段。就像ChatGPT那样逐字输出，视觉上比等1秒再刷出来舒服得多。



javascript

const eventSource = new EventSource('/api/stream')

eventSource.onmessage = (e) => {

&nbsp; setContent(prev => prev + e.data)

}

4\. 取消请求

结合loading状态和取消按钮。允许用户在等待时手动中断请求，提升控制感。特别是移动端，长时间无响应容易引发焦虑。



javascript

useEffect(() => {

&nbsp; const controller = new AbortController()

&nbsp; fetch(url, { signal: controller.signal })

&nbsp; return () => controller.abort()

}, \[])

5\. 缓存策略

缓存历史问答对。对于常见问题，比如产品FAQ，可以直接走本地缓存秒回，根本不用走大模型。



javascript

const cache = new Map()

if (cache.has(query)) {

&nbsp; setResult(cache.get(query))

} else {

&nbsp; const res = await fetchAPI(query)

&nbsp; cache.set(query, res)

}

6\. 降级策略

检测到连续超时，可以自动切换到轻量模型接口或静态回答模板，保证基础可用性。



评分标准：



5分：完整列出5种以上优化策略，有代码示例



3-4分：列出3-4种策略，但不够具体



1-2分：只知道1-2种策略



0分：完全不知道



第13题：如何使用React.lazy和Suspense实现代码拆分？(5分)

考察意图： 考察对React代码拆分的实践能力



问题： 如何使用React.lazy和Suspense实现代码拆分？有什么注意事项？



答案：



React.lazy和Suspense的作用：

React.lazy和Suspense是React内置的代码拆分方案，用于实现组件的按需加载，减少首屏JS体积，提升加载性能。



基本用法：



javascript

import React, { Suspense } from 'react'



// 使用React.lazy动态导入组件

const LazyComponent = React.lazy(() => import('./LazyComponent'))



function App() {

&nbsp; return (

&nbsp;   <div>

&nbsp;     <Suspense fallback={<div>Loading...</div>}>

&nbsp;       <LazyComponent />

&nbsp;     </Suspense>

&nbsp;   </div>

&nbsp; )

}

结合路由使用：



javascript

import { BrowserRouter, Routes, Route } from 'react-router-dom'

import React, { Suspense } from 'react'



const Home = React.lazy(() => import('./routes/Home'))

const About = React.lazy(() => import('./routes/About'))



function App() {

&nbsp; return (

&nbsp;   <BrowserRouter>

&nbsp;     <Suspense fallback={<div>Loading...</div>}>

&nbsp;       <Routes>

&nbsp;         <Route path="/" element={<Home />} />

&nbsp;         <Route path="/about" element={<About />} />

&nbsp;       </Routes>

&nbsp;     </Suspense>

&nbsp;   </BrowserRouter>

&nbsp; )

}

注意事项：



只能在Suspense组件内使用：React.lazy返回的组件必须包裹在Suspense组件中



只支持默认导出：React.lazy目前只支持默认导出（export default）



javascript

// 正确

export default MyComponent



// 错误

export const MyComponent = () => {}

多个懒加载组件：可以用多个Suspense分别处理，也可以用一个包裹多个



javascript

<Suspense fallback={<div>Loading...</div>}>

&nbsp; <LazyA />

&nbsp; <LazyB />

</Suspense>

错误边界：结合错误边界处理加载失败的情况



javascript

<ErrorBoundary fallback={<div>加载失败</div>}>

&nbsp; <Suspense fallback={<div>Loading...</div>}>

&nbsp;   <LazyComponent />

&nbsp; </Suspense>

</ErrorBoundary>

服务端渲染：React.lazy不支持SSR，需要使用@loadable/component等替代方案



评分标准：



5分：完整说明用法，指出注意事项，有代码示例



3-4分：基本说明用法，但注意事项不全



1-2分：知道概念，说不清用法



0分：完全不知道



第14题：如何优化RAG系统中的检索精度？(5分)

考察意图： 考察对AI应用性能优化的理解



问题： 在RAG应用中，如何优化检索精度？请列举至少5种方法。



答案：



RAG系统的检索精度直接影响最终答案的质量，以下是从多个维度优化的方法：



1\. 优化检索阶段的语义匹配能力



查询改写（Query Rewrite）：用大模型把原始query扩展成多个相关问法，再并行检索。LangChain提供了MultiQueryRetriever



自查询（Self-Query）：让模型自己生成查询条件，把复杂问题翻译成适合检索的结构化语句



python

\# 自查询示例

"去年销售额最高的产品" → 转成带时间约束的查询

2\. 提升文档切分的合理性



按语义切分：使用RecursiveCharacterTextSplitter结合sentence transformers做句子边界感知



chunk大小控制：一般256-512 token比较常见，具体得看内容密度



重叠窗口：滑动窗口重叠分块，避免语义断裂



3\. 引入重排序（Rerank）机制

初检可能召回一堆相关度一般的文档，加一层cross-encoder重排能显著提升TopK质量。像Cohere的reranker、bge-reranker都可以直接调用。



python

\# Rerank流程

pairs = \[(query, doc) for doc in retrieved\_docs]

scores = reranker.predict(pairs)

ranked\_docs = \[doc for \_, doc in sorted(zip(scores, retrieved\_docs), reverse=True)]

4\. 混合检索（Hybrid Search）



关键词检索：基于倒排索引，适合处理明确实体、过滤条件



向量检索：基于语义相似度，适合处理隐含关联



融合策略：加权、交叉排序（RRF）合并结果



5\. 调整生成阶段的提示工程



上下文约束：在prompt中明确"基于以下上下文回答"



只放高相关度的chunk：限制输入数量，避免噪音



来源标注：在prompt中标注信息来源，减少幻觉



6\. 数据清洗和预处理



清理噪声：去除HTML标签、广告文本、无意义符号



元数据注入：给每个chunk加上来源URL、标题、章节名等信息



实体增强：用spaCy或HanLP抽关键词、人名、地名，附加到chunk metadata里



7\. 反馈闭环

线上可以接反馈闭环，比如用户点赞/点踩驱动embedding或reranker微调，持续迭代。



评分标准：



5分：完整列出5种以上优化方法，说明原理



3-4分：列出3-4种方法，但不够具体



1-2分：只知道1-2种方法



0分：完全不知道



四、工程化与架构（共3题，15分）

第15题：微前端是什么？有哪些实现方式？(5分)

考察意图： 考察对前端架构的理解



问题： 什么是微前端？有哪些实现方式？各自的优缺点是什么？



答案：



微前端的定义：

微前端是一种将前端应用拆分成多个独立部署、独立开发的小型应用的架构风格。每个团队可以独立开发、测试、部署自己的模块，最终组合成一个完整应用。



核心价值：



技术栈无关：不同子应用可以使用不同框架（React、Vue、Angular）



独立开发部署：团队自治，加快迭代速度



增量升级：可以逐步重构老系统，而不是重写



主流实现方式：



1\. 路由分发式（最常用）



原理：通过Nginx或服务端路由，根据URL将请求分发到不同应用



优点：实现简单，完全解耦



缺点：体验不够流畅，切换需要刷新页面



适用：简单场景，不同模块独立部署



2\. iframe方案



原理：每个子应用用iframe嵌入



优点：天然隔离，样式和JS完全独立



缺点：路由难同步，通信复杂，体验差（刷新、滚动）



适用：遗留系统集成，第三方应用嵌入



3\. 微前端框架（single-spa、qiankun）



原理：通过路由匹配，动态加载和卸载子应用



优点：体验好，技术栈无关，支持子应用独立部署



缺点：有一定学习成本，需要处理公共依赖



javascript

// qiankun示例

registerMicroApps(\[

&nbsp; {

&nbsp;   name: 'react-app',

&nbsp;   entry: '//localhost:3000',

&nbsp;   container: '#container',

&nbsp;   activeRule: '/react'

&nbsp; }

])

start()

4\. Webpack Module Federation（模块联邦）



原理：Webpack 5内置，实现运行时共享模块



优点：可以实现依赖共享，减少重复代码



缺点：需要Webpack 5，配置较复杂



javascript

// webpack.config.js

new ModuleFederationPlugin({

&nbsp; name: 'app1',

&nbsp; remotes: {

&nbsp;   app2: 'app2@http://localhost:3002/remoteEntry.js'

&nbsp; }

})

5\. Web Components方案



原理：将子应用封装成自定义元素



优点：技术栈无关，标准规范



缺点：生态不够完善，复杂场景有坑



选型建议：



快速集成老项目用iframe



新建项目推荐qiankun



Webpack项目可用Module Federation



追求标准化考虑Web Components



评分标准：



5分：完整说明微前端概念，列举多种实现方式，分析优缺点



3-4分：基本说明概念，但实现方式不全



1-2分：知道概念，说不清实现



0分：完全不知道



第16题：CI/CD在前端项目中如何实践？(5分)

考察意图： 考察对前端工程化流程的理解



问题： CI/CD在前端项目中如何实践？请描述从代码提交到线上部署的完整流程。



答案：



CI/CD的定义：



CI（持续集成）：开发人员频繁将代码合并到主干，自动构建和测试



CD（持续交付/部署）：将经过测试的代码自动部署到生产环境



前端CI/CD完整流程：



1\. 代码提交（Git）



开发者提交代码到特性分支



触发pre-commit钩子（Husky + lint-staged）



代码格式化（Prettier）



ESLint检查



单元测试（Jest）



2\. 持续集成（CI）



yaml

\# .github/workflows/ci.yml示例

name: CI

on: \[push, pull\_request]



jobs:

&nbsp; build:

&nbsp;   runs-on: ubuntu-latest

&nbsp;   steps:

&nbsp;     - uses: actions/checkout@v2

&nbsp;     - uses: actions/setup-node@v2

&nbsp;       with:

&nbsp;         node-version: '16'

&nbsp;     - run: npm ci

&nbsp;     - run: npm run lint

&nbsp;     - run: npm run test

&nbsp;     - run: npm run build

3\. 构建产物处理



生成静态文件（HTML、CSS、JS、图片）



文件指纹（hash）处理



压缩优化（Gzip/Brotli）



上传到制品仓库（如OSS、Artifactory）



4\. 持续部署（CD）



测试环境：合并到develop分支后自动部署



预发环境：合并到release分支或打tag后部署



生产环境：手动触发或合并到main分支后部署



5\. 部署方式



静态托管：上传到OSS/CDN，配合Nginx



容器化部署：Docker构建镜像，K8s滚动更新



dockerfile

FROM nginx:alpine

COPY dist /usr/share/nginx/html

COPY nginx.conf /etc/nginx/nginx.conf

EXPOSE 80

6\. 监控与回滚



监控：接入Sentry错误监控，性能监控（LCP、FID等）



回滚策略：版本管理，蓝绿部署或金丝雀发布



前端CI/CD关键点：



环境变量管理：区分开发、测试、生产环境



版本控制：使用standard-version自动生成CHANGELOG



自动化测试：单元测试、E2E测试（Cypress）



通知机制：构建成功/失败通知到钉钉/飞书



评分标准：



5分：完整描述从提交到部署的全流程，有配置示例



3-4分：基本描述流程，但细节不够



1-2分：知道概念，说不清流程



0分：完全不知道



第17题：设计一个高可用的前端监控系统需要考虑哪些方面？(5分)

考察意图： 考察对前端稳定性和监控体系的理解



问题： 设计一个高可用的前端监控系统，需要考虑哪些方面？如何采集和上报数据？



答案：



前端监控系统的核心目标是及时发现、定位、解决问题，保证应用稳定性。设计时应考虑以下方面：



1\. 监控数据类型



类型	采集内容	用途

错误监控	JS运行时错误、Promise未捕获、资源加载失败	定位代码问题

性能监控	FCP、LCP、FID、TTFB、白屏时间	优化用户体验

行为监控	PV/UV、点击事件、页面停留时间	分析用户行为

自定义监控	业务关键指标	辅助业务决策

2\. 数据采集实现



javascript

// 错误监控示例

window.addEventListener('error', (e) => {

&nbsp; reporter.error({

&nbsp;   type: 'js\_error',

&nbsp;   message: e.message,

&nbsp;   stack: e.error?.stack,

&nbsp;   filename: e.filename,

&nbsp;   lineno: e.lineno,

&nbsp;   colno: e.colno

&nbsp; })

}, true)



// Promise错误

window.addEventListener('unhandledrejection', (e) => {

&nbsp; reporter.error({

&nbsp;   type: 'promise\_error',

&nbsp;   reason: e.reason

&nbsp; })

})



// 性能监控

window.addEventListener('load', () => {

&nbsp; const timing = performance.timing

&nbsp; reporter.performance({

&nbsp;   fcp: getFCP(),

&nbsp;   lcp: getLCP(),

&nbsp;   ttfb: timing.responseStart - timing.requestStart

&nbsp; })

})

3\. 数据上报策略



合并上报：收集多条数据后批量发送



采样上报：用户量大时按比例采样



降级策略：网络差时本地存储，恢复后上报



图片打点：使用new Image().src上报（无跨域问题）



4\. 异常处理机制



JS错误：使用try-catch包裹关键代码



资源加载：<link>和<script>添加onerror处理



白屏检测：定时检查根元素是否有内容



javascript

// 白屏检测示例

function checkWhiteScreen() {

&nbsp; const elements = document.querySelectorAll('#app \*')

&nbsp; if (elements.length === 0) {

&nbsp;   reporter.error({ type: 'white\_screen' })

&nbsp; }

}

setTimeout(checkWhiteScreen, 3000)

5\. 上报接口设计



javascript

// 上报数据结构

{

&nbsp; appId: 'your-app',      // 应用标识

&nbsp; timestamp: 1634567890,  // 时间戳

&nbsp; userId: 'user-123',     // 用户标识

&nbsp; sessionId: 'session-456', // 会话标识

&nbsp; url: window.location.href,

&nbsp; ua: navigator.userAgent,

&nbsp; data: { ... }  // 具体监控数据

}

6\. 服务端处理



数据清洗：去重、格式化



聚合分析：按错误类型、浏览器、版本统计



告警规则：错误率突增、新错误出现时告警



数据可视化：Dashboard展示核心指标



7\. 注意事项



隐私合规：不上报用户敏感信息



性能影响：上报代码体积要小，不能阻塞主流程



自监控：监控系统自身不能出问题



评分标准：



5分：完整说明监控体系各方面，有代码示例和架构思考



3-4分：基本说明监控要点，但不够全面



1-2分：只知道错误监控，其他不了解



0分：完全不知道



五、复杂场景处理（共3题，15分）

第18题：设计一个支持百万级连接的WebSocket服务，需要考虑哪些问题？(5分)

考察意图： 考察对高并发实时通信系统的理解



问题： 设计一个支持百万级连接的WebSocket服务，需要考虑哪些问题？如何保证性能和稳定性？



答案：



支持百万级WebSocket连接是一个复杂的系统工程，需要从多个层面考虑：



1\. 网络层优化



TCP参数调优：



bash

\# 调整文件描述符限制

fs.file-max = 1000000



\# 调整TCP keepalive

net.ipv4.tcp\_keepalive\_time = 300

net.ipv4.tcp\_keepalive\_intvl = 30

net.ipv4.tcp\_keepalive\_probes = 3



\# 端口范围

net.ipv4.ip\_local\_port\_range = 1024 65535

负载均衡：使用LVS、Nginx或HAProxy做四层转发



集群部署：多节点部署，通过一致性哈希分配连接



2\. 服务端架构



javascript

// 使用Node.js + cluster模块利用多核

const cluster = require('cluster')

const numCPUs = require('os').cpus().length



if (cluster.isMaster) {

&nbsp; for (let i = 0; i < numCPUs; i++) {

&nbsp;   cluster.fork()

&nbsp; }

} else {

&nbsp; const WebSocket = require('ws')

&nbsp; const wss = new WebSocket.Server({ port: 8080 })

}

多进程/多线程：充分利用CPU核心



I/O多路复用：使用epoll（Linux）或IOCP（Windows）



协程/异步：避免阻塞，提高并发



3\. 内存管理



连接对象优化：轻量化连接对象，减少内存占用



消息队列：使用Redis或Kafka做消息缓冲



心跳机制：及时清理无效连接



javascript

// 心跳检测示例

setInterval(() => {

&nbsp; wss.clients.forEach((ws) => {

&nbsp;   if (ws.isAlive === false) {

&nbsp;     return ws.terminate()

&nbsp;   }

&nbsp;   ws.isAlive = false

&nbsp;   ws.ping()

&nbsp; })

}, 30000)

4\. 消息处理策略



消息压缩：使用permessage-deflate扩展



消息分片：大消息拆分发送



优先级队列：重要消息优先发送



消息持久化：使用Redis持久化离线消息



5\. 水平扩展方案



javascript

// 使用Redis做连接状态共享

const Redis = require('ioredis')

const pub = new Redis()

const sub = new Redis()



// 广播消息

function broadcast(message) {

&nbsp; pub.publish('channel', JSON.stringify(message))

}



// 其他节点订阅

sub.subscribe('channel', (err, count) => {

&nbsp; sub.on('message', (channel, message) => {

&nbsp;   // 转发给本地连接

&nbsp;   wss.clients.forEach(client => {

&nbsp;     client.send(message)

&nbsp;   })

&nbsp; })

})

6\. 监控和告警



连接数监控：QPS、连接总数



内存监控：堆内存使用情况



错误监控：连接异常断开、超时等



告警阈值：设置合理阈值及时报警



7\. 容灾和降级



多机房部署：主备切换



连接降级：WebSocket失败后降级为长轮询



限流熔断：防止流量冲击



评分标准：



5分：全面考虑网络、架构、内存、扩展、监控各方面



3-4分：基本考虑主要问题，但不够全面



1-2分：只知道简单部署，没有扩展思路



0分：完全不知道



第19题：如何实现一个支持断点续传的大文件上传功能？(5分)

考察意图： 考察对文件上传复杂场景的处理能力



问题： 如何实现一个支持断点续传的大文件上传功能？请描述前端实现思路。



答案：



断点续传的核心思想是将大文件切分成小块，分别上传，记录上传进度。当上传中断后，可以只上传未完成的部分。



1\. 文件切片



javascript

function createFileChunks(file, chunkSize = 2 \* 1024 \* 1024) {

&nbsp; const chunks = \[]

&nbsp; let start = 0

&nbsp; while (start < file.size) {

&nbsp;   const end = Math.min(start + chunkSize, file.size)

&nbsp;   chunks.push(file.slice(start, end))

&nbsp;   start = end

&nbsp; }

&nbsp; return chunks

}

2\. 生成文件标识



javascript

// 使用SparkMD5计算文件hash

async function calculateFileHash(file) {

&nbsp; return new Promise((resolve) => {

&nbsp;   const spark = new SparkMD5.ArrayBuffer()

&nbsp;   const reader = new FileReader()

&nbsp;   

&nbsp;   reader.onload = (e) => {

&nbsp;     spark.append(e.target.result)

&nbsp;     resolve(spark.end())

&nbsp;   }

&nbsp;   

&nbsp;   reader.readAsArrayBuffer(file)

&nbsp; })

}

3\. 上传管理



javascript

class UploadManager {

&nbsp; constructor(file, chunkSize = 2 \* 1024 \* 1024) {

&nbsp;   this.file = file

&nbsp;   this.chunkSize = chunkSize

&nbsp;   this.uploadedChunks = new Set() // 已上传的切片索引

&nbsp;   this.abortController = new AbortController()

&nbsp; }



&nbsp; // 检查已上传状态

&nbsp; async checkUploadStatus(fileHash) {

&nbsp;   const res = await fetch(`/api/upload/status?fileHash=${fileHash}`)

&nbsp;   const data = await res.json()

&nbsp;   this.uploadedChunks = new Set(data.uploadedChunks)

&nbsp;   return data

&nbsp; }



&nbsp; // 上传切片

&nbsp; async uploadChunk(chunk, index, fileHash) {

&nbsp;   const formData = new FormData()

&nbsp;   formData.append('chunk', chunk)

&nbsp;   formData.append('index', index)

&nbsp;   formData.append('fileHash', fileHash)

&nbsp;   formData.append('fileName', this.file.name)



&nbsp;   return fetch('/api/upload/chunk', {

&nbsp;     method: 'POST',

&nbsp;     body: formData,

&nbsp;     signal: this.abortController.signal

&nbsp;   })

&nbsp; }



&nbsp; // 并发控制上传

&nbsp; async upload(concurrency = 3) {

&nbsp;   const chunks = createFileChunks(this.file, this.chunkSize)

&nbsp;   const fileHash = await calculateFileHash(this.file)

&nbsp;   

&nbsp;   await this.checkUploadStatus(fileHash)

&nbsp;   

&nbsp;   const tasks = chunks.map((chunk, index) => {

&nbsp;     if (this.uploadedChunks.has(index)) {

&nbsp;       return Promise.resolve() // 已上传跳过

&nbsp;     }

&nbsp;     return () => this.uploadChunk(chunk, index, fileHash)

&nbsp;   })



&nbsp;   // 并发控制

&nbsp;   return asyncPool(tasks, concurrency)

&nbsp; }



&nbsp; // 暂停上传

&nbsp; pause() {

&nbsp;   this.abortController.abort()

&nbsp; }



&nbsp; // 合并文件

&nbsp; async merge() {

&nbsp;   return fetch('/api/upload/merge', {

&nbsp;     method: 'POST',

&nbsp;     body: JSON.stringify({

&nbsp;       fileHash: this.fileHash,

&nbsp;       fileName: this.file.name,

&nbsp;       totalChunks: this.totalChunks

&nbsp;     })

&nbsp;   })

&nbsp; }

}

4\. 进度计算



javascript

function calculateProgress(uploadedChunks, totalChunks) {

&nbsp; return Math.round((uploadedChunks.size / totalChunks) \* 100)

}

5\. 服务端处理



接收切片：将切片保存到临时目录



记录状态：记录已上传的切片索引



合并文件：所有切片上传完成后，按顺序合并



校验：计算合并后的文件hash，与前端一致



javascript

// Node.js服务端伪代码

app.post('/api/upload/merge', async (req, res) => {

&nbsp; const { fileHash, fileName, totalChunks } = req.body

&nbsp; const chunksDir = path.join(\_\_dirname, 'uploads', fileHash)

&nbsp; 

&nbsp; // 按索引顺序合并

&nbsp; const writeStream = fs.createWriteStream(fileName)

&nbsp; for (let i = 0; i < totalChunks; i++) {

&nbsp;   const chunkPath = path.join(chunksDir, i.toString())

&nbsp;   const data = await fs.promises.readFile(chunkPath)

&nbsp;   writeStream.write(data)

&nbsp; }

&nbsp; writeStream.end()

&nbsp; 

&nbsp; res.json({ success: true })

})

6\. 用户体验优化



暂停/恢复：支持用户手动暂停恢复



断网重连：网络恢复后自动续传



进度条：实时显示上传进度



错误重试：失败切片自动重试3次



评分标准：



5分：完整实现切片、hash、状态检查、并发控制、暂停恢复



3-4分：基本实现切片上传，但功能不够完善



1-2分：只知道思路，没有具体实现



0分：完全不知道



第20题：如何实现一个前端错误监控SDK？(5分)

考察意图： 考察对错误监控体系的实践能力



问题： 如何实现一个前端错误监控SDK？需要考虑哪些错误类型？如何上报？



答案：



一个完整的前端错误监控SDK需要捕获各种类型的错误，并合理上报。



1\. SDK核心结构



javascript

class ErrorMonitor {

&nbsp; constructor(options = {}) {

&nbsp;   this.appId = options.appId

&nbsp;   this.url = options.url

&nbsp;   this.sampleRate = options.sampleRate || 1 // 采样率

&nbsp;   this.queue = \[] // 上报队列

&nbsp;   this.init()

&nbsp; }



&nbsp; init() {

&nbsp;   // 捕获JS运行时错误

&nbsp;   this.catchJSError()

&nbsp;   // 捕获Promise错误

&nbsp;   this.catchPromiseError()

&nbsp;   // 捕获资源加载错误

&nbsp;   this.catchResourceError()

&nbsp;   // 捕获未处理的异步错误

&nbsp;   this.catchAsyncError()

&nbsp;   // 监控性能指标

&nbsp;   this.monitorPerformance()

&nbsp;   // 设置上报定时器

&nbsp;   this.setupReporter()

&nbsp; }

}

2\. 捕获JS运行时错误



javascript

catchJSError() {

&nbsp; window.addEventListener('error', (event) => {

&nbsp;   // 过滤资源加载错误，由catchResourceError处理

&nbsp;   if (event.target \&\& (event.target.tagName || event.target.src)) {

&nbsp;     return

&nbsp;   }

&nbsp;   

&nbsp;   this.send({

&nbsp;     type: 'js\_error',

&nbsp;     message: event.message,

&nbsp;     filename: event.filename,

&nbsp;     lineno: event.lineno,

&nbsp;     colno: event.colno,

&nbsp;     stack: event.error?.stack,

&nbsp;     timestamp: Date.now()

&nbsp;   })

&nbsp; }, true)

}

3\. 捕获Promise错误



javascript

catchPromiseError() {

&nbsp; window.addEventListener('unhandledrejection', (event) => {

&nbsp;   let message = ''

&nbsp;   let stack = ''

&nbsp;   

&nbsp;   if (event.reason instanceof Error) {

&nbsp;     message = event.reason.message

&nbsp;     stack = event.reason.stack

&nbsp;   } else {

&nbsp;     message = String(event.reason)

&nbsp;   }

&nbsp;   

&nbsp;   this.send({

&nbsp;     type: 'promise\_error',

&nbsp;     message,

&nbsp;     stack,

&nbsp;     timestamp: Date.now()

&nbsp;   })

&nbsp; })

}

4\. 捕获资源加载错误



javascript

catchResourceError() {

&nbsp; // 图片、脚本、样式等资源加载失败

&nbsp; window.addEventListener('error', (event) => {

&nbsp;   const target = event.target

&nbsp;   if (target \&\& (target.tagName || target.src)) {

&nbsp;     this.send({

&nbsp;       type: 'resource\_error',

&nbsp;       tagName: target.tagName,

&nbsp;       src: target.src || target.href,

&nbsp;       timestamp: Date.now()

&nbsp;     })

&nbsp;   }

&nbsp; }, true)

}

5\. 监控性能指标



javascript

monitorPerformance() {

&nbsp; // 使用PerformanceObserver监听LCP

&nbsp; if (PerformanceObserver) {

&nbsp;   const lcpObserver = new PerformanceObserver((list) => {

&nbsp;     const entries = list.getEntries()

&nbsp;     const lastEntry = entries\[entries.length - 1]

&nbsp;     this.send({

&nbsp;       type: 'performance',

&nbsp;       metric: 'LCP',

&nbsp;       value: lastEntry.startTime,

&nbsp;       timestamp: Date.now()

&nbsp;     })

&nbsp;   })

&nbsp;   lcpObserver.observe({ entryTypes: \['largest-contentful-paint'] })

&nbsp; }



&nbsp; // 页面加载完成后上报核心指标

&nbsp; window.addEventListener('load', () => {

&nbsp;   const timing = performance.timing

&nbsp;   this.send({

&nbsp;     type: 'performance',

&nbsp;     metrics: {

&nbsp;       fcp: this.getFCP(),

&nbsp;       domReady: timing.domContentLoadedEventEnd - timing.navigationStart,

&nbsp;       loadComplete: timing.loadEventEnd - timing.navigationStart,

&nbsp;       ttfb: timing.responseStart - timing.requestStart

&nbsp;     },

&nbsp;     timestamp: Date.now()

&nbsp;   })

&nbsp; })

}

6\. 数据上报策略



javascript

// 合并上报

send(data) {

&nbsp; // 采样控制

&nbsp; if (Math.random() > this.sampleRate) return

&nbsp; 

&nbsp; this.queue.push(data)

&nbsp; 

&nbsp; // 队列满立即上报

&nbsp; if (this.queue.length >= 10) {

&nbsp;   this.flush()

&nbsp; }

}



// 定时上报

setupReporter() {

&nbsp; setInterval(() => {

&nbsp;   if (this.queue.length > 0) {

&nbsp;     this.flush()

&nbsp;   }

&nbsp; }, 5000) // 5秒上报一次

}



// 实际上报

flush() {

&nbsp; if (this.queue.length === 0) return

&nbsp; 

&nbsp; const data = this.queue.slice()

&nbsp; this.queue = \[]

&nbsp; 

&nbsp; // 使用sendBeacon（页面卸载时也能上报）

&nbsp; if (navigator.sendBeacon) {

&nbsp;   const blob = new Blob(\[JSON.stringify(data)], { type: 'application/json' })

&nbsp;   navigator.sendBeacon(this.url, blob)

&nbsp; } else {

&nbsp;   // 降级为图片打点

&nbsp;   const img = new Image()

&nbsp;   img.src = `${this.url}?data=${encodeURIComponent(JSON.stringify(data))}`

&nbsp; }

}

7\. 用户和环境信息



javascript

getUserInfo() {

&nbsp; return {

&nbsp;   appId: this.appId,

&nbsp;   url: window.location.href,

&nbsp;   userAgent: navigator.userAgent,

&nbsp;   language: navigator.language,

&nbsp;   screen: `${window.screen.width}x${window.screen.height}`,

&nbsp;   viewport: `${window.innerWidth}x${window.innerHeight}`,

&nbsp;   timestamp: Date.now(),

&nbsp;   sessionId: this.getSessionId()

&nbsp; }

}



getSessionId() {

&nbsp; let sessionId = sessionStorage.getItem('monitor\_session\_id')

&nbsp; if (!sessionId) {

&nbsp;   sessionId = `${Date.now()}-${Math.random().toString(36).substr(2)}`

&nbsp;   sessionStorage.setItem('monitor\_session\_id', sessionId)

&nbsp; }

&nbsp; return sessionId

}

8\. SDK使用示例



javascript

// 初始化

const monitor = new ErrorMonitor({

&nbsp; appId: 'your-app',

&nbsp; url: 'https://monitor.your.com/report',

&nbsp; sampleRate: 0.1 // 10%采样

})



// 手动上报

monitor.send({

&nbsp; type: 'custom',

&nbsp; category: 'business',

&nbsp; action: 'submit\_order',

&nbsp; data: { orderId: '123' }

})

9\. 注意事项



性能影响：不能阻塞主线程，不能影响页面性能



隐私合规：不上报用户敏感信息



自监控：监控SDK自身不能出问题



容错处理：上报失败要降级处理



评分标准：



5分：完整实现各种错误捕获，有上报策略、采样、用户信息



3-4分：基本实现错误捕获，但功能不够完善



1-2分：只知道概念，没有具体实现



0分：完全不知道



📊 面试评分表

类别	题目数量	满分	得分

React深入	5	25	

Vue深入	5	25	

性能优化	4	20	

工程化与架构	3	15	

复杂场景处理	3	15	

总计	20	100	

评分等级：



85-100分：优秀，二面通过，推荐三面（总监面）



70-84分：良好，待定，需结合一面表现



60-69分：及格，基础尚可但深度不够



0-59分：不及格，不建议通过



📝 二面面试官点评要点

通过标准（85分以上）：

对React/Vue原理有深入理解，能说清底层机制



有性能优化实战经验，能提出具体方案



架构设计思维清晰，能应对复杂场景



代码能力扎实，能写出完整实现



待加强点（70-84分）：

原理理解不够深入，停留在API使用层面



性能优化经验不足，方案不够具体



复杂场景处理能力有待提升



不通过标准（60分以下）：

只知用法，不知原理



没有性能优化意识



复杂场景无从下手



工程化思维薄弱

