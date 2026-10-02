export type Field = {
  key: string;
  label: string;
  type?: string;
  value?: string;
  options?: string[];
  help?: string;
  optional?: boolean;
  when?: [string, string[]];
};
export type Tool = {
  id: string;
  name: string;
  description: string;
  path: string;
  fields: Field[];
  role?: string;
  read?: boolean;
  params?: (v: Record<string, string>) => unknown;
};
const today = new Date().toISOString().slice(0, 10);
const future = new Date(Date.now() + 90 * 86400000).toISOString().slice(0, 10);
const past = new Date(Date.now() - 366 * 86400000).toISOString().slice(0, 10);
const f = (
  key: string,
  label: string,
  value = "",
  type = "text",
  optional = false,
): Field => ({ key, label, value, type, optional });
const sel = (
  key: string,
  label: string,
  options: string[],
  value = options[0],
): Field => ({ key, label, options, value });
const perf = f("performanceID", "Performance ID", "5", "number");
const dates = [
  f("from", "From", today, "date"),
  f("to", "Through", future, "date"),
];
const period = [
  f("from", "From", past, "date"),
  f("to", "Through", today, "date"),
];
const location = [
  f("latitude", "Latitude", "43.6426", "number"),
  f("longitude", "Longitude", "-79.3871", "number"),
  f("radiusKm", "Radius · km", "15", "number"),
  sel("rankBy", "Sort by", ["distance", "price_asc", "price_desc"]),
];
export const queries: Tool[] = [
  {
    id: "q1",
    name: "Nearby events",
    description:
      "Find upcoming shows within a radius, ranked by distance or price.",
    path: "query/q1",
    read: true,
    fields: location,
  },
  {
    id: "q2",
    name: "Postal neighborhood",
    description:
      "Search an exact postal area and its explicitly linked neighbors.",
    path: "query/q2",
    read: true,
    fields: [f("postalCode", "Postal code", "M5J 2X2")],
  },
  {
    id: "q3",
    name: "Exact address",
    description:
      "Find a venue and every upcoming performance at its street address.",
    path: "query/q3",
    read: true,
    fields: [f("address", "Venue street address", "40 Bay Street")],
  },
  {
    id: "q4",
    name: "Time & availability",
    description:
      "Combine a geographic search with dates and minimum available inventory.",
    path: "query/q4",
    read: true,
    fields: [
      ...dates,
      f("minAvailable", "Minimum tickets", "1", "number"),
      sel("geography", "Geographic area", [
        "ALL",
        "VICINITY",
        "POSTAL",
        "ADDRESS",
      ]),
      ...location.map((x) => ({
        ...x,
        when: ["geography", ["VICINITY"]] as [string, string[]],
      })),
      {
        ...f("postalCode", "Postal code", "M5J 2X2"),
        when: ["geography", ["POSTAL"]],
      },
      {
        ...f("address", "Street address", "40 Bay Street"),
        when: ["geography", ["ADDRESS"]],
      },
    ],
  },
  {
    id: "q5",
    name: "Find your show",
    description:
      "Combine city, classification, dates, budget and seating preferences.",
    path: "query/q5",
    read: true,
    fields: [
      f("city", "City", "", undefined, true),
      f("segmentName", "Segment", "", undefined, true),
      f("genreName", "Genre", "", undefined, true),
      ...dates.map((x) => ({ ...x, optional: true })),
      f("minPrice", "Minimum price", "", "number", true),
      f("maxPrice", "Maximum price", "", "number", true),
      f("minAvailable", "Minimum tickets", "1", "number"),
      sel("sectionType", "Seating", ["ANY", "RESERVED", "GA"]),
    ],
  },
  {
    id: "q6",
    name: "Seat availability",
    description:
      "Compare available, sold and blocked inventory across sections and tiers.",
    path: "query/q6",
    read: true,
    fields: [perf],
  },
  {
    id: "q7",
    name: "Sit together",
    description:
      "Find the best consecutive reserved seats for your group and budget.",
    path: "query/q7",
    read: true,
    fields: [
      perf,
      f("quantity", "Group size", "2", "number"),
      f("budget", "Total budget", "500", "number", true),
    ],
  },
];
export const reports: Tool[] = (
  [
    {
      id: "r1",
      name: "Sales revenue",
      description:
        "Gross original ticket sales by city or one venue during a chosen period.",
      path: "report/r1",
      fields: [
        sel("mode", "Group by", ["city", "venue"]),
        ...period,
        f("city", "City", "", undefined, true),
        {
          ...f("venueID", "Venue ID", "1", "number"),
          when: ["mode", ["venue"]],
        },
      ],
      params: (v) => ({ params: [v.mode, v.from, v.to, v.city, v.venueID] }),
    },
    {
      id: "r2",
      name: "Programming mix",
      description:
        "Event and performance counts across classifications and geography.",
      path: "report/r2",
      fields: [sel("mode", "Geographic detail", ["country", "city", "venue"])],
      params: (v) => ({ params: [v.mode] }),
    },
    {
      id: "r3",
      name: "Organizer rankings",
      description:
        "Rank organizers by revenue overall, within countries or within cities.",
      path: "report/r3",
      fields: [
        sel("mode", "Ranking scope", ["overall", "country", "city"]),
        ...period,
      ],
      params: (v) => ({ params: [v.mode, v.from, v.to] }),
    },
    {
      id: "r4",
      name: "Resale behavior",
      description:
        "Customers with at least ten acquisitions and more than half listed in a city.",
      path: "report/r4",
      fields: [f("days", "Lookback · days", "365", "number")],
      params: (v) => ({ params: [v.days] }),
    },
    {
      id: "r5",
      name: "Customer activity",
      description:
        "Rank eligible customers by primary and resale orders in your selected period.",
      path: "report/r5",
      fields: [
        sel("mode", "Ranking scope", ["overall", "per-city"]),
        ...period,
      ],
      params: (v) => ({ params: [v.mode, v.from, v.to] }),
    },
    {
      id: "r6",
      name: "Cancellations",
      description:
        "Cancellation counts attributed to the customer or organizer who initiated them.",
      path: "report/r6",
      fields: [
        sel("mode", "Account type", ["both", "customers", "organizers"]),
        f("days", "Lookback · days", "365", "number"),
        f("limit", "Top results", "10", "number"),
      ],
      params: (v) => ({ params: [v.mode, v.days, v.limit] }),
    },
    {
      id: "r7",
      name: "Sell-through",
      description:
        "Inventory utilization by performance, tier, or monthly sold-out and low-sales shows.",
      path: "report/r7",
      fields: [
        sel("mode", "View", ["perf", "tier", "month"]),
        { ...perf, optional: true, when: ["mode", ["perf", "tier"]] },
        {
          ...f("month", "Month", today.slice(0, 7), "month"),
          when: ["mode", ["month"]],
        },
        {
          ...f("city", "City", "", undefined, true),
          when: ["mode", ["month"]],
        },
      ],
      params: (v) => ({
        params:
          v.mode === "month"
            ? [v.mode, v.month, v.city]
            : [v.mode, v.performanceID],
      }),
    },
    {
      id: "r8",
      name: "Resale market",
      description:
        "Completed resale volume, markups and listing prices at their historical caps.",
      path: "report/r8",
      fields: [
        sel("mode", "View", ["stats", "top10"]),
        ...period.map((x) => ({
          ...x,
          when: ["mode", ["top10"]] as [string, string[]],
        })),
      ],
      params: (v) => ({
        params: v.mode === "top10" ? [v.mode, v.from, v.to] : [v.mode],
      }),
    },
    {
      id: "r9",
      name: "Audience voices",
      description:
        "The ten most frequent noun phrases per event, aggregated from review occurrences.",
      path: "report/r9",
      fields: [f("eventID", "Event ID", "", "number", true)],
      params: (v) => ({ params: [v.eventID] }),
    },
  ] satisfies Tool[]
).map((x) => ({ ...x, read: true }));
export const customer: Tool[] = [
  {
    id: "discover",
    name: "Upcoming events",
    description:
      "Choose an event, then open its performances or explore seating.",
    path: "catalog/events",
    read: true,
    fields: [],
  },
  {
    id: "performances",
    name: "Event performances",
    description: "Upcoming dates and locations for one event.",
    path: "catalog/performances",
    read: true,
    fields: [f("eventID", "Event ID", "2", "number")],
  },
  {
    id: "seats",
    name: "Available seats",
    description: "Reserved seats currently available for purchase.",
    path: "catalog/seats",
    read: true,
    fields: [perf],
  },
  {
    id: "map",
    name: "Section inventory",
    description:
      "Section IDs, seating kinds and remaining capacity for booking.",
    path: "catalog/seat-map",
    read: true,
    fields: [perf],
  },
  {
    id: "book",
    name: "Book tickets",
    description:
      "Choose reserved seat IDs, a standing section quantity, or both. Payment is simulated.",
    path: "customer/book",
    role: "CUSTOMER",
    fields: [
      perf,
      {
        ...f("seatIDs", "Reserved seat IDs", "", undefined, true),
        help: "Comma separated, e.g. 145,146. Find IDs in Available seats.",
      },
      f("gaSectionID", "Standing section ID", "", "number", true),
      f("gaQuantity", "Standing tickets", "0", "number"),
    ],
  },
  {
    id: "tickets",
    name: "My tickets",
    description: "Tickets you currently own, with original face values.",
    path: "catalog/tickets",
    role: "CUSTOMER",
    read: true,
    fields: [],
  },
  {
    id: "history",
    name: "Purchase history",
    description:
      "Your acquisition records and amounts paid, including completed resales.",
    path: "catalog/history",
    role: "CUSTOMER",
    read: true,
    fields: [],
  },
  {
    id: "detail",
    name: "Ticket journey",
    description: "Inspect one ticket from your own purchase history.",
    path: "catalog/ticket",
    role: "CUSTOMER",
    read: true,
    fields: [f("ticketID", "Ticket ID", "", "number")],
  },
  {
    id: "cancel",
    name: "Cancel a ticket",
    description:
      "Refund your current acquisition when more than seven days remain before the show.",
    path: "customer/cancel",
    role: "CUSTOMER",
    fields: [f("ticketID", "Ticket ID", "", "number")],
  },
  {
    id: "resale",
    name: "Resale marketplace",
    description:
      "Active resale listings with face value, asking price and seller.",
    path: "catalog/resale",
    read: true,
    fields: [],
  },
  {
    id: "list",
    name: "List for resale",
    description: "Offer a ticket you own, within the event’s price cap.",
    path: "customer/list",
    role: "CUSTOMER",
    fields: [
      f("ticketID", "Ticket ID", "", "number"),
      f("askingPrice", "Asking price", "", "number"),
    ],
  },
  {
    id: "my-listings",
    name: "My active listings",
    description: "Your currently available resale offers.",
    path: "catalog/my-listings",
    read: true,
    role: "CUSTOMER",
    fields: [],
  },
  {
    id: "withdraw",
    name: "Withdraw a listing",
    description: "Remove your active offer from the resale marketplace.",
    path: "customer/withdraw",
    role: "CUSTOMER",
    fields: [f("listingID", "Listing ID", "", "number")],
  },
  {
    id: "buy",
    name: "Buy a resale ticket",
    description:
      "Acquire an active listing at its asking price. Ownership transfers atomically.",
    path: "customer/buy",
    role: "CUSTOMER",
    fields: [f("listingID", "Listing ID", "", "number")],
  },
  {
    id: "reviewable",
    name: "Shows to review",
    description:
      "Completed performances you attended and have not reviewed yet.",
    path: "catalog/reviewable",
    read: true,
    role: "CUSTOMER",
    fields: [],
  },
  {
    id: "review",
    name: "Write a review",
    description: "Rate your event and venue, then share what stood out.",
    path: "customer/review",
    role: "CUSTOMER",
    fields: [
      perf,
      sel("eventRating", "Event rating", ["5", "4", "3", "2", "1"]),
      sel("venueRating", "Venue rating", ["5", "4", "3", "2", "1"]),
      f("comment", "Your review", "", "textarea"),
    ],
  },
];
export const organizer: Tool[] = [
  {
    id: "my-events",
    name: "My events",
    description: "Events managed by your organizer account.",
    path: "catalog/my-events",
    read: true,
    fields: [],
  },
  {
    id: "my-performances",
    name: "My performances",
    description: "Your scheduled and cancelled performances.",
    path: "catalog/my-performances",
    read: true,
    fields: [],
  },
  {
    id: "event",
    name: "Create an event",
    description:
      "Choose a genre and ordered performer IDs from the data explorer.",
    path: "organizer/event",
    fields: [
      f("title", "Event title"),
      f("genreID", "Genre ID", "1", "number"),
      f("artistIDs", "Artist IDs in billing order", "1,2"),
      f("resaleCapRatio", "Resale cap ratio", "1.20", "number"),
    ],
  },
  {
    id: "performance",
    name: "Schedule a performance",
    description: "Define prices and map every venue section to one tier.",
    path: "organizer/performance",
    fields: [
      f("eventID", "Your event ID", "", "number"),
      f("venueID", "Venue ID", "1", "number"),
      f("date", "Date", future, "date"),
      f("startTime", "Starts", "19:00", "time"),
      f("endTime", "Ends", "22:00", "time"),
      {
        ...f("tierPrices", "Tier prices", '{"Standard": 60}', "textarea"),
        help: "JSON object: tier name → price.",
      },
      {
        ...f(
          "sectionToTier",
          "Section assignments",
          '{"1":"Standard","2":"Standard","3":"Standard"}',
          "textarea",
        ),
        help: "JSON object: section ID → tier name. Use every section of the selected venue.",
      },
    ],
  },
  {
    id: "suggest",
    name: "Pricing suggestions",
    description:
      "Compare recent performances and inspect confidence before choosing prices.",
    path: "organizer/suggest",
    read: true,
    fields: [
      f("venueID", "Venue ID", "1", "number"),
      f("genreID", "Genre ID", "1", "number"),
      f("date", "Target date", future, "date"),
    ],
  },
  {
    id: "estimate",
    name: "Revenue estimate",
    description:
      "Estimate the change in revenue from a price adjustment using the documented toolkit model.",
    path: "organizer/estimate",
    read: true,
    fields: [
      f("suggestedPrice", "Suggested price", "60", "number"),
      f("capacity", "Tier capacity units", "100", "number"),
      f("sellThrough", "Sell-through fraction", "0.7", "number"),
      f("delta", "Price change fraction", "0.05", "number"),
    ],
  },
  {
    id: "tiers",
    name: "Performance tiers",
    description:
      "Prices and historical sales; previously sold tiers cannot be repriced.",
    path: "catalog/tiers",
    read: true,
    fields: [perf],
  },
  {
    id: "price",
    name: "Change a tier price",
    description: "Adjust an unsold tier of a future performance you organize.",
    path: "organizer/price",
    fields: [
      f("tierID", "Tier ID", "", "number"),
      f("price", "New price", "", "number"),
    ],
  },
  {
    id: "block",
    name: "Block a seat",
    description: "Take an unsold seat out of inventory for your performance.",
    path: "organizer/block",
    fields: [
      perf,
      f("seatID", "Seat ID", "", "number"),
      f("reason", "Reason", "Production hold"),
    ],
  },
  {
    id: "blocked",
    name: "Blocked seats",
    description: "Inspect performance-specific seat holds.",
    path: "catalog/blocked",
    read: true,
    fields: [perf],
  },
  {
    id: "unblock",
    name: "Release a seat",
    description: "Return a blocked seat to inventory.",
    path: "organizer/unblock",
    fields: [perf, f("seatID", "Seat ID", "", "number")],
  },
  {
    id: "cancel",
    name: "Cancel a performance",
    description:
      "Cancel your future performance and refund all active acquisitions.",
    path: "organizer/cancel",
    fields: [perf],
  },
].map((x) => ({ ...x, role: "ORGANIZER" }));
